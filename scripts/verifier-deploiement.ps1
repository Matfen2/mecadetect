<#
.SYNOPSIS
  Vérifie le déploiement de production de MécaDétect (tâche 5.5).

.DESCRIPTION
  Démarre compose.prod.yaml sur le poste, sous un nom de projet à part
  (mecadetect-verif) et avec des secrets aléatoires, puis vérifie :
    - les refus de démarrage quand un secret manque ;
    - la santé des 4 services et le profil prod de l'API ;
    - l'isolement : seul Caddy publie des ports, le reste est sans Internet ;
    - le durcissement : système de fichiers en lecture seule, capacités retirées,
      redémarrage automatique, journaux bornés ;
    - le comportement HTTPS : redirection, routage, en-têtes de sécurité.
  Le projet est supprimé à la fin, volumes compris (sauf avec -Garder).
  La base de développement (projet mecadetect) n'est pas touchée.

.EXAMPLE
  .\scripts\verifier-deploiement.ps1
  .\scripts\verifier-deploiement.ps1 -Garder
#>
param(
    [switch] $Garder
)

$Projet = 'mecadetect-verif'
$FichierCompose = 'compose.prod.yaml'
$PortHttp = 28080
$PortHttps = 28443
$VerificationsAttendues = 25
$Https = "https://localhost:$PortHttps"

$curl = 'curl.exe'
if ($PSVersionTable.PSEdition -eq 'Core' -and -not $IsWindows) { $curl = 'curl' }

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

function Aleatoire([int] $octets) {
    $tampon = New-Object byte[] $octets
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($tampon)
    return ,$tampon # tableau d'octets entier, non déroulé
}

# Mot de passe alphanumérique : accepté par Oracle sans guillemets
function MotDePasse {
    return 'Verif' + (((Aleatoire 18) | ForEach-Object { '{0:x2}' -f $_ }) -join '')
}

function EcrireEnvironnement([string] $chemin, [hashtable] $valeurs) {
    $lignes = $valeurs.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }
    Set-Content -Path $chemin -Value $lignes -Encoding Ascii
}

function Compose([string] $fichierEnv, [string[]] $arguments) {
    & docker compose -p $Projet -f $FichierCompose --env-file $fichierEnv @arguments
}

# Requête HTTPS vers Caddy (-k : certificat de l'autorité locale de Caddy)
function Http([string] $url, [string[]] $entetes = @(), [switch] $SansCorps) {
    $neant = 'NUL'
    if ($curl -eq 'curl') { $neant = '/dev/null' }
    $arguments = @('-s', '-k', '--max-time', '10', '-i')
    if ($SansCorps) { $arguments = @('-s', '-k', '--max-time', '10', '-D', '-', '-o', $neant) }
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

function Conteneur([string] $service) {
    return ((Compose $FichierEnv @('ps', '-q', $service)) -join '').Trim()
}

# (pas « Configuration » : mot réservé de Windows PowerShell, pour DSC)
function ConfigurationHote([string] $service) {
    $id = Conteneur $service
    if ($id -eq '') { return $null }
    return ((& docker inspect --format '{{json .HostConfig}}' $id) -join '') | ConvertFrom-Json
}

function Reseaux([string] $service) {
    $id = Conteneur $service
    if ($id -eq '') { return @() }
    $json = ((& docker inspect --format '{{json .NetworkSettings.Networks}}' $id) -join '') | ConvertFrom-Json
    return @($json.PSObject.Properties | ForEach-Object { $_.Name })
}

function PortsPublies($configuration) {
    if ($null -eq $configuration -or $null -eq $configuration.PortBindings) { return 0 }
    return @($configuration.PortBindings.PSObject.Properties).Count
}

# ---------------------------------------------------------------------------

if (-not (Test-Path $FichierCompose) -or -not (Test-Path 'deploiement/Caddyfile')) {
    Write-Host "À lancer depuis la racine du dépôt ($FichierCompose et deploiement/Caddyfile introuvables)." -ForegroundColor Red
    exit 2
}

# Les variables du terminal l'emporteraient sur le fichier d'environnement
foreach ($nom in 'DOMAINE', 'VERSION', 'PORT_HTTP', 'PORT_HTTPS', 'ORACLE_PASSWORD', 'ORACLE_APP_USER', 'ORACLE_APP_PASSWORD', 'JWT_SECRET') {
    Remove-Item "Env:$nom" -ErrorAction SilentlyContinue
}

$dossierTemporaire = Join-Path ([IO.Path]::GetTempPath()) "mecadetect-verif-$PID"
New-Item -ItemType Directory -Path $dossierTemporaire -Force | Out-Null
$FichierEnv = Join-Path $dossierTemporaire 'env.verif'

$valeurs = @{
    DOMAINE             = 'localhost'
    VERSION             = 'verif'
    PORT_HTTP           = "$PortHttp"
    PORT_HTTPS          = "$PortHttps"
    ORACLE_PASSWORD     = MotDePasse
    ORACLE_APP_USER     = 'mecadetect'
    ORACLE_APP_PASSWORD = MotDePasse
    JWT_SECRET          = [Convert]::ToBase64String((Aleatoire 32))
}
EcrireEnvironnement $FichierEnv $valeurs

try {
    # ------------------------------------------------------------ Secrets
    Titre 'Secrets obligatoires'
    foreach ($nom in 'JWT_SECRET', 'ORACLE_APP_PASSWORD', 'DOMAINE') {
        $incomplet = Join-Path $dossierTemporaire "sans-$nom"
        $partiel = $valeurs.Clone()
        $partiel.Remove($nom)
        EcrireEnvironnement $incomplet $partiel
        $sortie = (& docker compose -p $Projet -f $FichierCompose --env-file $incomplet config --quiet 2>&1) -join ' '
        Verifier "sans $nom, Docker Compose refuse de démarrer" ($LASTEXITCODE -ne 0 -and $sortie -match $nom) "(code : $LASTEXITCODE)"
    }
    & git check-ignore -q .env.prod
    Verifier '.env.prod est ignoré par git' ($LASTEXITCODE -eq 0)

    # ------------------------------------------------------------ Démarrage
    # Un reste d'exécution précédente (-Garder) garderait une base créée avec
    # d'autres secrets aléatoires : Oracle refuserait alors le nouveau mot de passe
    Compose $FichierEnv @('down', '-v', '--remove-orphans') 2>&1 | Out-Null

    Titre 'Démarrage (premier lancement : création de la base, quelques minutes)'
    Compose $FichierEnv @('up', '-d', '--build', '--wait', '--wait-timeout', '900') | Out-Host
    $demarre = ($LASTEXITCODE -eq 0)
    Verifier 'les 4 services démarrent et sont en bonne santé' $demarre

    if (-not $demarre) {
        Compose $FichierEnv @('ps', '-a') | Out-Host
        Compose $FichierEnv @('logs', '--tail', '30', 'api', 'caddy') | Out-Host
        NonFaites 'déploiement non démarré' 20
    } else {
        $journalApi = (Compose $FichierEnv @('logs', 'api') 2>&1) -join "`n"
        Verifier "l'API tourne avec le profil prod" ($journalApi -match 'profiles? (is|are) active: "prod"')

        # -------------------------------------------------------- Isolement
        Titre 'Isolement'
        $configurations = @{}
        foreach ($service in 'caddy', 'front', 'api', 'oracle') { $configurations[$service] = ConfigurationHote $service }

        $publies = @('front', 'api', 'oracle') | Where-Object { (PortsPublies $configurations[$_]) -gt 0 }
        Verifier 'seul Caddy publie des ports' ((PortsPublies $configurations['caddy']) -gt 0 -and @($publies).Count -eq 0) "(publient aussi : $($publies -join ', '))"

        $interne = ((& docker network inspect --format '{{.Internal}}' "${Projet}_interne" 2>$null) -join '').Trim()
        $exposes = @('front', 'api', 'oracle') | Where-Object { (Reseaux $_) -contains "${Projet}_exterieur" }
        Verifier 'front, API et Oracle sur un réseau interne seulement' ($interne -eq 'true' -and @($exposes).Count -eq 0) "(réseau interne : '$interne', exposés : $($exposes -join ', '))"

        Compose $FichierEnv @('exec', '-T', 'api', 'bash', '-c', 'exec 3<>/dev/tcp/1.1.1.1/443') 2>$null | Out-Null
        Verifier "l'API ne peut pas joindre Internet" ($LASTEXITCODE -ne 0)

        # -------------------------------------------------------- Durcissement
        Titre 'Durcissement'
        $publics = @('caddy', 'front', 'api')
        $ecrivables = $publics | Where-Object { -not $configurations[$_].ReadonlyRootfs }
        Verifier 'Caddy, front et API en lecture seule' (@($ecrivables).Count -eq 0) "(en écriture : $($ecrivables -join ', '))"

        $privilegies = $publics | Where-Object {
            $c = $configurations[$_]
            -not (@($c.CapDrop) -contains 'ALL') -or -not (@($c.SecurityOpt) -contains 'no-new-privileges:true')
        }
        Verifier 'capacités retirées et no-new-privileges (Caddy, front, API)' (@($privilegies).Count -eq 0) "(à revoir : $($privilegies -join ', '))"

        $sansRedemarrage = @('caddy', 'front', 'api', 'oracle') | Where-Object { $configurations[$_].RestartPolicy.Name -ne 'unless-stopped' }
        Verifier 'redémarrage automatique des 4 services' (@($sansRedemarrage).Count -eq 0) "(à revoir : $($sansRedemarrage -join ', '))"

        $sansLimite = @('caddy', 'front', 'api', 'oracle') | Where-Object { $configurations[$_].LogConfig.Config.'max-size' -ne '10m' }
        Verifier 'journaux bornés pour les 4 services' (@($sansLimite).Count -eq 0) "(à revoir : $($sansLimite -join ', '))"

        # -------------------------------------------------------- HTTPS
        Titre 'HTTPS et routage'
        $http = Http "http://localhost:$PortHttp/"
        Verifier 'http redirige vers https' ($http.Statut -eq 308 -and (Entete $http 'Location') -like 'https://*') "(statut : $($http.Statut), Location : '$(Entete $http 'Location')')"

        $accueil = Http "$Https/"
        Verifier "https : l'application Angular" ($accueil.Statut -eq 200 -and $accueil.Corps -match '<app-root') "(statut : $($accueil.Statut))"

        $profond = Http "$Https/interventions/42"
        Verifier 'https : lien direct vers une route Angular' ($profond.Statut -eq 200 -and $profond.Corps -match '<app-root') "(statut : $($profond.Statut))"

        $api = Http "$Https/api/v1/interventions"
        Verifier "/api arrive à l'API (401 sans jeton)" ($api.Statut -eq 401) "(statut : $($api.Statut))"

        Verifier '/actuator non publié (404)' ((Http "$Https/actuator/health").Statut -eq 404)

        $documentation = Http "$Https/swagger-ui/index.html" -SansCorps
        Verifier 'documentation de l''API publiée' ($documentation.Statut -eq 200) "(statut : $($documentation.Statut))"

        $bundle = [regex]::Match($accueil.Corps, '(main-[A-Za-z0-9]{8}\.js)').Groups[1].Value
        $compresse = Http "$Https/$bundle" @('Accept-Encoding: zstd, gzip') -SansCorps
        Verifier 'JavaScript compressé' ($bundle -ne '' -and (Entete $compresse 'Content-Encoding') -match 'zstd|gzip') "(fichier : '$bundle', Content-Encoding : '$(Entete $compresse 'Content-Encoding')')"

        # -------------------------------------------------------- En-têtes
        Titre 'En-têtes de sécurité'
        Verifier 'HSTS' ((Entete $accueil 'Strict-Transport-Security') -match 'max-age=\d+') "('$(Entete $accueil 'Strict-Transport-Security')')"
        $csp = Entete $accueil 'Content-Security-Policy'
        Verifier "CSP : scripts du site seulement, pas d'affichage en cadre" ($csp -match "script-src 'self'" -and $csp -match "frame-ancestors 'none'") "('$csp')"
        Verifier 'nosniff et Referrer-Policy' ((Entete $accueil 'X-Content-Type-Options') -eq 'nosniff' -and (Entete $accueil 'Referrer-Policy') -ne '')
        Verifier 'en-têtes aussi posés sur les réponses de l''API' ((Entete $api 'Content-Security-Policy') -ne '' -and (Entete $api 'Strict-Transport-Security') -ne '')
        Verifier 'aucun en-tête Server (ni Caddy, ni Nginx)' ((Entete $accueil 'Server') -eq '' -and (Entete $api 'Server') -eq '') "(front : '$(Entete $accueil 'Server')', API : '$(Entete $api 'Server')')"
    }
} finally {
    if ($Garder) {
        Write-Host "`nProjet $Projet conservé : https://localhost:$PortHttps" -ForegroundColor Yellow
        Write-Host "Pour l'arrêter : docker compose -p $Projet -f $FichierCompose --env-file `"$FichierEnv`" down -v" -ForegroundColor Yellow
    } else {
        Titre 'Nettoyage'
        Compose $FichierEnv @('down', '-v', '--remove-orphans') 2>&1 | Out-Null
        Remove-Item $dossierTemporaire -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host '  projet de vérification supprimé, volumes compris'
    }
}

# ---------------------------------------------------------------- Bilan
$total = $script:reussites + $script:echecs
Write-Host ''
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