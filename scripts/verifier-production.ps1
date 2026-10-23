<#
.SYNOPSIS
  Vérifie le site de production de MécaDétect depuis Internet (tâche 5.6).

.DESCRIPTION
  À lancer depuis le poste, une fois le site déployé :
    - nom de domaine et certificat HTTPS valide (sans -k : certificat public) ;
    - redirection, en-têtes de sécurité, sondes internes non publiées ;
    - comptes de démonstration, droits par rôle, données générées ;
    - base de données et API injoignables directement depuis Internet.

.EXAMPLE
  .\scripts\verifier-production.ps1 -Domaine mecadetect.duckdns.org
#>
param(
    [Parameter(Mandatory)] [string] $Domaine,
    [string] $MotDePasseDemo = 'Demo2026!',
    [int] $InterventionsMinimum = 100000
)

$VerificationsAttendues = 15
$Https = "https://$Domaine"

# Windows PowerShell 5 n'active pas TLS 1.2 par défaut
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

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

function Titre([string] $texte) { Write-Host "`n== $texte ==" -ForegroundColor Cyan }

# Requête via curl : statut, en-têtes, corps, et code de sortie de curl
# (60 = certificat refusé). Pas de -k : le certificat doit être reconnu.
function Http([string] $url) {
    $lignes = @(& $curl -s -i --max-time 15 $url 2>$null)
    $reponse = @{ Statut = 0; Entetes = @{}; Corps = ''; CodeCurl = $LASTEXITCODE }
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

# Appel JSON de l'API : statut et contenu décodé
function Api([string] $methode, [string] $chemin, $corps = $null, [string] $jeton = '') {
    $entetes = @{}
    if ($jeton -ne '') { $entetes['Authorization'] = "Bearer $jeton" }
    $parametres = @{
        Uri = "$Https/api/v1$chemin"; Method = $methode; Headers = $entetes
        UseBasicParsing = $true; TimeoutSec = 30; ErrorAction = 'Stop'
    }
    if ($null -ne $corps) {
        $parametres['Body'] = [Text.Encoding]::UTF8.GetBytes(($corps | ConvertTo-Json))
        $parametres['ContentType'] = 'application/json; charset=utf-8'
    }
    try {
        $r = Invoke-WebRequest @parametres
        $contenu = $null
        if ($r.Content) { $contenu = $r.Content | ConvertFrom-Json }
        return @{ Statut = [int]$r.StatusCode; Contenu = $contenu }
    } catch {
        $statut = 0
        if ($_.Exception.Response) { $statut = [int]$_.Exception.Response.StatusCode }
        return @{ Statut = $statut; Contenu = $null }
    }
}

function Connexion([string] $email) {
    return Api 'POST' '/auth/login' @{ email = $email; motDePasse = $MotDePasseDemo }
}

# Connexion TCP directe : doit échouer pour les ports internes
function PortOuvert([string] $hote, [int] $port) {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $tentative = $client.BeginConnect($hote, $port, $null, $null)
        if (-not $tentative.AsyncWaitHandle.WaitOne(3000)) { return $false }
        $client.EndConnect($tentative)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

# ---------------------------------------------------------------------------

Titre "Domaine et HTTPS ($Domaine)"
$adresses = @()
try { $adresses = @([Net.Dns]::GetHostAddresses($Domaine) | ForEach-Object { $_.IPAddressToString }) } catch { }
Verifier 'le nom de domaine pointe vers le serveur' ($adresses.Count -gt 0) "(adresses : $($adresses -join ', '))"
if ($adresses.Count -gt 0) { Write-Host "         adresse : $($adresses -join ', ')" }

$accueil = Http "$Https/"
Verifier 'certificat HTTPS reconnu par les navigateurs' ($accueil.CodeCurl -eq 0 -and $accueil.Statut -eq 200) "(curl : $($accueil.CodeCurl), statut : $($accueil.Statut))"

$http = Http "http://$Domaine/"
Verifier 'http redirige vers https' ($http.Statut -eq 308 -and (Entete $http 'Location') -like "https://$Domaine*") "(statut : $($http.Statut))"

Verifier "l'application Angular est servie" ($accueil.Corps -match '<app-root')
$csp = Entete $accueil 'Content-Security-Policy'
Verifier 'en-têtes de sécurité (HSTS et CSP)' ((Entete $accueil 'Strict-Transport-Security') -match 'max-age' -and $csp -match "script-src 'self'")
Verifier '/actuator non publié (404)' ((Http "$Https/actuator/health").Statut -eq 404)
Verifier "documentation de l'API publiée" ((Http "$Https/swagger-ui/index.html").Statut -eq 200)

Titre 'Comptes de démonstration'
$admin = Connexion 'admin@mecadetect.fr'
Verifier 'connexion administrateur' ($admin.Statut -eq 200 -and $admin.Contenu.token) "(statut : $($admin.Statut))"
$technicien = Connexion 'technicien@mecadetect.fr'
Verifier 'connexion technicien' ($technicien.Statut -eq 200 -and $technicien.Contenu.token) "(statut : $($technicien.Statut))"
$demandeur = Connexion 'demandeur@mecadetect.fr'
Verifier 'connexion demandeur' ($demandeur.Statut -eq 200 -and $demandeur.Contenu.token) "(statut : $($demandeur.Statut))"

Titre 'Droits et données'
$jetonAdmin = ''
if ($admin.Contenu) { $jetonAdmin = $admin.Contenu.token }
$jetonTechnicien = ''
if ($technicien.Contenu) { $jetonTechnicien = $technicien.Contenu.token }

Verifier "tableau de bord accessible à l'administrateur" ((Api 'GET' '/dashboard' $null $jetonAdmin).Statut -eq 200)
Verifier 'tableau de bord refusé au technicien (403)' ((Api 'GET' '/dashboard' $null $jetonTechnicien).Statut -eq 403)

$liste = Api 'GET' '/interventions' $null $jetonAdmin
$total = 0
if ($liste.Contenu) { $total = [long]$liste.Contenu.totalElements }
Verifier "au moins $InterventionsMinimum interventions de démonstration" ($total -ge $InterventionsMinimum) "($total)"
Write-Host "         interventions : $total"

Titre 'Ports internes fermés depuis Internet'
$cible = $Domaine
if ($adresses.Count -gt 0) { $cible = $adresses[0] }
Verifier 'Oracle (1521) injoignable' (-not (PortOuvert $cible 1521))
Verifier 'API en direct (8080) injoignable' (-not (PortOuvert $cible 8080))

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