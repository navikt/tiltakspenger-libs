Felles testkode for tiltakspenger-repoer.
Ikke legg inn avhengigheter som ikke er felles for alle repoer/moduler.
Eksempel på generelle avhengigheter på tvers av alle prosjekter: arrow, slf4j, kotlin-logging

## WireMock-hjelpere

`no.nav.tiltakspenger.libs.common` (i `common/WiremockEx.kt`) er den ene felles kilden til WireMock-oppsett for alle tiltakspenger-repoer:

- `withWireMockServer { server -> ... }` — starter en IPv4-trygg server, kjører blokka, og stopper serveren uansett om blokka kaster.
  Funksjonen er `inline`, så den fungerer både i synkrone tester og i `suspend`-tester.
- `stoppedServerUri(path)` — returnerer en URI mot en port der ingen lytter (for å teste nettverksfeil / "server ikke kontaktbar").
- `ipv4WireMockServer()` — rå factory dersom du må styre livssyklusen selv.

Hjelperne binder til `127.0.0.1` og rapporterer `127.0.0.1` i URL-ene i stedet for "localhost".
Det unngår en flaky kollisjon på macOS der "localhost" resolver til IPv6 (`::1`) og treffer en lokal IPv6-tjeneste (typisk AirPlay Receiver) på samme efemer-port.
Bruk disse i stedet for å lage egne `WireMockServer(0)`-varianter i hvert repo.


