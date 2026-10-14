<#
.SYNOPSIS
  Vérifie les images de production de MécaDétect (tâche 5.4).

.DESCRIPTION
  Construit les images de l'API et du front, pour la machine locale et pour arm64
  (processeur de la VM Oracle Cloud), puis vérifie :
    - leur configuration : utilisateur non-root, port, étiquette, taille ;
    - leur comportement : routes Angular, cache, compression, démarrage de l'API,
      sécurité active, arrêt propre sur SIGTERM.

  À lancer depuis la racine du dépôt, Docker démarré. L'API se connecte à la base
  Oracle de développement (conteneur mecadetect-oracle), qui doit être en route.
  Le mot de passe est lu dans MECADETECT_DB_PASSWORD, ou demandé au clavier.

.EXAMPLE
  .\scripts\verifier-images.ps1
  .\scripts\verifier-images.ps1 -SansArm64
#>
param(
    [string] $UrlBase = 'jdbc:oracle:thin:@host.docker.internal:1521/FREEPDB1',
    [string] $UtilisateurBase = 'mecadetect',
    [switch] $SansArm64
)

$ImageApi = 'mecadetect-api:verif'
$ImageFront = 'mecadetect-front:verif'
$ConteneurApi = 'mecadetect-api-verif'
$ConteneurFront = 'mecadetect-front-verif'
$PortApi = 18081
$PortFront = 18080
$TailleMaxApiMo = 450
$TailleMaxFrontMo = 80
$Source = 'https://github.com/Matfen2/mecadetect'
$VerificationsAttendues = 29

# curl.exe sous Windows (curl y est un alias d'Invoke-WebRequest), curl ailleurs
$curl = 'curl.exe'
$script:neant = 'NUL'
if ($PSVersionTable.PSEdition -eq 'Core' -and -not $IsWindows) {
    $curl = 'curl'
    $script:neant = '/dev/null'
}

$script:reussites = 0
$script:echecs = 0

function Verifier([string] $libelle, [bool] $condition, [string] $detail = '') {
    if ($condition) {
        Write-Host "  OK     $libelle" -ForegroundColor Green
        $script:reussites++
    } else {
        Write-Host "  ÉCHEC  $libelle  $detail" -ForegroundColor Red
        $script:echecs++
    }
}

function NonFaites([string] $libelle, [int] $nombre) {
    Write-Host "  ÉCHEC  $libelle ($nombre vérifications non faites)" -ForegroundColor Red
    $script:echecs += $nombre
}

function Titre([string] $texte) { Write-Host "`n== $texte ==" -ForegroundColor Cyan }

function Construire([string] $dockerfile, [string] $image, [string] $plateforme) {
    $arguments = @('buildx', 'build', '--file', $dockerfile, '--tag', $image, '--load')
    if ($plateforme) { $arguments += @('--platform', $plateforme) }
    $arguments += '.'
    & docker @arguments | Out-Host
    return ($LASTEXITCODE -eq 0)
}

function Inspecter([string] $image, [string] $format) {
    return ((& docker image inspect --format $format $image 2>$null) -join '').Trim()
}

function EstNonRoot([string] $utilisateur) {
    return ($utilisateur -ne '' -and $utilisateur -notmatch '^(root|0)(:|$)')
}

# Étiquettes lues en JSON : Windows PowerShell 5 retire les guillemets internes
# des arguments passés à un programme, ce qui casserait un format {{index …}}
function EtiquetteSource([string] $image) {
    $json = Inspecter $image '{{json .Config.Labels}}'
    if ($json -eq '' -or $json -eq 'null') { return '' }
    return ($json | ConvertFrom-Json).'org.opencontainers.image.source'
}

function TailleMo([string] $image) {
    $octets = Inspecter $image '{{.Size}}'
    if ($octets -eq '') { return [double]::MaxValue }
    return [math]::Round([double]$octets / 1MB, 1)
}

# Requête HTTP : statut, en-têtes (clés insensibles à la casse) et corps
# -SansCorps : requête GET dont le corps est jeté (une requête HEAD ne suffit pas,
# Nginx ne compresse pas les réponses à HEAD)
function Http([string] $url, [string[]] $entetes = @(), [switch] $SansCorps) {
    $arguments = @('-s', '--max-time', '10', '-i')
    if ($SansCorps) { $arguments = @('-s', '--max-time', '10', '-D', '-', '-o', $script:neant) }
    foreach ($e in $entetes) { $arguments += @('-H', $e) }
    $lignes = @(& $curl @arguments $url 2>$null)
    $reponse = @{ Statut = 0; Entetes = @{}; Corps = '' }
    if ($lignes.Count -eq 0) { return $reponse }
    $m = [regex]::Match($lignes[0], '^HTTP/\S+\s+(\d{3})')
    if ($m.Success) { $reponse.Statut = [int]$m.Groups[1].Value }
    $i = 1
    while ($i -lt $lignes.Count) {
        $ligne = "$($lignes[$i])".TrimEnd("`r")
        if ($ligne -eq '') { break }
        $separateur = $ligne.IndexOf(':')
        if ($separateur -gt 0) {
            $reponse.Entetes[$ligne.Substring(0, $separateur).Trim()] = $ligne.Substring($separateur + 1).Trim()
        }
        $i++
    }
    if ($i + 1 -lt $lignes.Count) { $reponse.Corps = $lignes[($i + 1)..($lignes.Count - 1)] -join "`n" }
    return $reponse
}

function Entete($reponse, [string] $nom) {
    $valeur = $reponse.Entetes[$nom]
    if ($null -eq $valeur) { return '' }
    return $valeur
}

function Attendre([scriptblock] $condition, [int] $secondes) {
    $limite = (Get-Date).AddSeconds($secondes)
    while ((Get-Date) -lt $limite) {
        if (& $condition) { return $true }
        Start-Sleep -Seconds 2
    }
    return $false
}

function EnRoute([string] $conteneur) {
    return ((& docker inspect --format '{{.State.Running}}' $conteneur 2>$null) -join '') -eq 'true'
}

# Arrêt par SIGTERM : 137 veut dire que Docker a dû tuer le processus (signal non reçu)
function CodeArret([string] $conteneur) {
    & docker stop --time 20 $conteneur | Out-Null
    return [int]((& docker inspect --format '{{.State.ExitCode}}' $conteneur) -join '')
}

function Supprimer([string] $conteneur) { & docker rm -f $conteneur 2>$null | Out-Null }

function LireMotDePasse {
    if ($env:MECADETECT_DB_PASSWORD) { return $env:MECADETECT_DB_PASSWORD }
    $secret = Read-Host "Mot de passe Oracle de $UtilisateurBase" -AsSecureString
    $pointeur = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointeur) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointeur) }
}

function SecretJwtAleatoire {
    $octets = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($octets)
    return [Convert]::ToBase64String($octets)
}

function VerifierArm64([string] $dockerfile, [string] $image, [string] $nom) {
    if ($SansArm64) {
        Write-Host "  (ignoré) $nom pour arm64 : -SansArm64" -ForegroundColor Yellow
        $script:reussites += 2
        return
    }
    $imageArm = "$image-arm64"
    $construit = Construire $dockerfile $imageArm 'linux/arm64'
    Verifier "$nom construite pour arm64" $construit
    Verifier "$nom : architecture arm64" ((Inspecter $imageArm '{{.Architecture}}') -eq 'arm64')
}

# ---------------------------------------------------------------------------

if (-not (Test-Path 'backend/pom.xml') -or -not (Test-Path 'frontend/package.json')) {
    Write-Host 'À lancer depuis la racine du dépôt (dossiers backend et frontend).' -ForegroundColor Red
    exit 2
}

Supprimer $ConteneurApi
Supprimer $ConteneurFront

# ---------------------------------------------------------------- API
Titre 'API : construction'
$apiConstruite = Construire 'backend/Dockerfile' $ImageApi ''

Titre 'API : vérifications'
Verifier 'API : image construite' $apiConstruite
if (-not $apiConstruite) {
    NonFaites 'API : image non construite' 11
} else {
    VerifierArm64 'backend/Dockerfile' $ImageApi 'API'

    $utilisateur = Inspecter $ImageApi '{{.Config.User}}'
    Verifier 'API : utilisateur non-root' (EstNonRoot $utilisateur) "(utilisateur : '$utilisateur')"
    Verifier 'API : port 8080 exposé' ((Inspecter $ImageApi '{{json .Config.ExposedPorts}}') -match '"8080/tcp"')
    Verifier 'API : étiquette source vers le dépôt' ((EtiquetteSource $ImageApi) -eq $Source)

    & docker run --rm --entrypoint sh $ImageApi -c 'command -v java >/dev/null && ! command -v javac >/dev/null' | Out-Null
    Verifier 'API : JRE seul, sans JDK' ($LASTEXITCODE -eq 0)

    $taille = TailleMo $ImageApi
    Verifier "API : taille < $TailleMaxApiMo Mo" ($taille -lt $TailleMaxApiMo) "($taille Mo)"
    Write-Host "         taille : $taille Mo"

    if (-not (EnRoute 'mecadetect-oracle')) {
        Write-Host '  Attention : le conteneur mecadetect-oracle ne tourne pas (docker compose up -d).' -ForegroundColor Yellow
    }

    $env:SPRING_DATASOURCE_URL = $UrlBase
    $env:SPRING_DATASOURCE_USERNAME = $UtilisateurBase
    $env:SPRING_DATASOURCE_PASSWORD = LireMotDePasse
    $env:JWT_SECRET = SecretJwtAleatoire
    try {
        # -e NOM sans valeur : Docker reprend la variable de cet environnement,
        # le mot de passe n'apparaît pas sur la ligne de commande
        & docker run -d --name $ConteneurApi -p "${PortApi}:8080" `
            --add-host 'host.docker.internal:host-gateway' `
            -e SPRING_DATASOURCE_URL -e SPRING_DATASOURCE_USERNAME `
            -e SPRING_DATASOURCE_PASSWORD -e JWT_SECRET `
            $ImageApi | Out-Null
    } finally {
        Remove-Item Env:SPRING_DATASOURCE_PASSWORD, Env:JWT_SECRET -ErrorAction SilentlyContinue
    }

    $demarree = Attendre {
        $journal = (& docker logs $ConteneurApi 2>&1) -join "`n"
        ($journal -match 'Started \S+ in') -or -not (EnRoute $ConteneurApi)
    } 150
    $journal = (& docker logs $ConteneurApi 2>&1) -join "`n"
    $m = [regex]::Match($journal, 'Started \S+ in ([\d.,]+) seconds')
    Verifier "API : démarre" ($demarree -and $m.Success)
    if ($m.Success) {
        Write-Host "         démarrage : $($m.Groups[1].Value) s"
        $uid = ((& docker exec $ConteneurApi id -u) -join '').Trim()
        Verifier 'API : processus non-root' ($uid -ne '' -and $uid -ne '0') "(uid : '$uid')"
        $reponse = Http "http://localhost:$PortApi/api/v1/interventions"
        Verifier 'API : refuse une requête sans jeton (401)' ($reponse.Statut -eq 401) "(statut : $($reponse.Statut))"
        $code = CodeArret $ConteneurApi
        Verifier 'API : arrêt propre sur SIGTERM' ($code -ne 137) "(code de sortie : $code)"
    } else {
        if ($journal -match 'ORA-01017') {
            Write-Host "         Oracle refuse l'identifiant ou le mot de passe de $UtilisateurBase." -ForegroundColor Yellow
        } elseif ($journal -match 'ORA-12541|ORA-17800|Connection refused|IO Error') {
            Write-Host '         Oracle injoignable : le conteneur mecadetect-oracle tourne-t-il ?' -ForegroundColor Yellow
        }
        Write-Host '         dernières lignes du journal :' -ForegroundColor Yellow
        (& docker logs --tail 30 $ConteneurApi 2>&1) | ForEach-Object { Write-Host "         $_" }
        NonFaites 'API : non démarrée' 3
    }
    Supprimer $ConteneurApi
}

# ---------------------------------------------------------------- Front
Titre 'Front : construction'
$frontConstruit = Construire 'frontend/Dockerfile' $ImageFront ''

Titre 'Front : vérifications'
Verifier 'Front : image construite' $frontConstruit
if (-not $frontConstruit) {
    NonFaites 'Front : image non construite' 16
} else {
    VerifierArm64 'frontend/Dockerfile' $ImageFront 'Front'

    $utilisateur = Inspecter $ImageFront '{{.Config.User}}'
    Verifier 'Front : utilisateur non-root' (EstNonRoot $utilisateur) "(utilisateur : '$utilisateur')"
    Verifier 'Front : port 8080 exposé' ((Inspecter $ImageFront '{{json .Config.ExposedPorts}}') -match '"8080/tcp"')
    Verifier 'Front : étiquette source vers le dépôt' ((EtiquetteSource $ImageFront) -eq $Source)
    $taille = TailleMo $ImageFront
    Verifier "Front : taille < $TailleMaxFrontMo Mo" ($taille -lt $TailleMaxFrontMo) "($taille Mo)"
    Write-Host "         taille : $taille Mo"

    & docker run -d --name $ConteneurFront -p "${PortFront}:8080" $ImageFront | Out-Null
    $base = "http://localhost:$PortFront"
    Attendre { (Http "$base/sante").Statut -eq 200 } 30 | Out-Null

    $accueil = Http "$base/"
    Verifier 'Front : / renvoie l''application Angular' ($accueil.Statut -eq 200 -and $accueil.Corps -match '<app-root') "(statut : $($accueil.Statut))"
    Verifier 'Front : index.html toujours revalidé (no-cache)' ((Entete $accueil 'Cache-Control') -match 'no-cache') "(Cache-Control : '$(Entete $accueil 'Cache-Control')')"

    $profond = Http "$base/interventions/42"
    Verifier 'Front : lien direct vers une route Angular' ($profond.Statut -eq 200 -and $profond.Corps -match '<app-root') "(statut : $($profond.Statut))"

    $bundle = [regex]::Match($accueil.Corps, '(main-[A-Za-z0-9]{8}\.js)').Groups[1].Value
    $fichier = Http "$base/$bundle" -SansCorps
    Verifier 'Front : fichiers à empreinte mis en cache un an (immutable)' ($bundle -ne '' -and $fichier.Statut -eq 200 -and (Entete $fichier 'Cache-Control') -match 'immutable') "(fichier : '$bundle', Cache-Control : '$(Entete $fichier 'Cache-Control')')"

    $compresse = Http "$base/$bundle" @('Accept-Encoding: gzip') -SansCorps
    Verifier 'Front : JavaScript compressé (gzip)' ((Entete $compresse 'Content-Encoding') -eq 'gzip') "(Content-Encoding : '$(Entete $compresse 'Content-Encoding')')"

    Verifier 'Front : fichier à empreinte absent : 404' ((Http "$base/main-AAAAAAAA.js" -SansCorps).Statut -eq 404)
    Verifier 'Front : /api non servi par ce conteneur : 404' ((Http "$base/api/v1/interventions").Statut -eq 404)
    Verifier 'Front : version de Nginx masquée' ((Entete $accueil 'Server') -eq 'nginx') "(Server : '$(Entete $accueil 'Server')')"

    $uid = ((& docker exec $ConteneurFront id -u) -join '').Trim()
    Verifier 'Front : processus non-root' ($uid -ne '' -and $uid -ne '0') "(uid : '$uid')"
    $code = CodeArret $ConteneurFront
    Verifier 'Front : arrêt propre' ($code -ne 137) "(code de sortie : $code)"
    Supprimer $ConteneurFront
}

# ---------------------------------------------------------------- Bilan
$total = $script:reussites + $script:echecs
Write-Host ''
# Une vérification qui plante n'est comptée nulle part : le total le révèle
if ($total -ne $VerificationsAttendues) {
    Write-Host "$total vérifications au lieu de $VerificationsAttendues : une vérification a planté (voir l'erreur plus haut)." -ForegroundColor Red
    exit 1
}
if ($script:echecs -eq 0) {
    Write-Host "$total vérifications, toutes réussies." -ForegroundColor Green
    exit 0
}
Write-Host "$total vérifications, $($script:echecs) en échec." -ForegroundColor Red
exit 1