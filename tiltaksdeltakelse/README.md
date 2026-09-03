# tiltaksdeltakelse

Domenemodell for tiltaksdeltakelser, delt mellom `tiltakspenger-saksbehandling-api` og `tiltakspenger-soknad-api`.

Modulen erstatter gradvis appen `tiltakspenger-tiltak` og DTO-ene i `tiltak-dtos`.
Se [navikt/tiltakspenger#41](https://github.com/navikt/tiltakspenger/issues/41).

## Moduler

| Modul | Innhold |
|---|---|
| `tiltaksdeltakelse-domene` | Domenemodellen. Ingen eksterne avhengigheter |
| `tiltaksdeltakelse-infrastruktur` | Kontraktskopien av `tiltakshistorikk`, kodetabellene og mappingen til domenet. Klientene og hente-tjenesten er under utbygging, og ingen konsument bruker modulen ennå |

## Hva modulen modellerer

To ting, holdt fra hverandre med vilje.

**Kilden.** `Kildestatus` bærer statusen kildesystemet selv oppga, ordrett — `Arenastatus`, `Kometstatus`, `TeamTiltakstatus`.
En kode vi ikke kjenner igjen flyter inn som kildens egen `Ukjent`-variant, bæres ordrett, og blokkerer tolkning til den er mappet.
`Tiltaksdeltakelse` bærer resten av saksopplysningen: datoer, arrangør, omfang, og tiltakskoden slik kilden skrev den.

**Vår tolkning.** `Deltakerstatus` er domenets egen ordlyd, tre kategorier som avgjør rett: `DeltarEllerHarDeltatt`, `TildeltIkkeStartet` og `IkkeDeltatt`.

Normaliseringen mellom dem er en faglig vurdering, ikke en teknisk oversettelse, og ligger derfor i domenet der den kan granskes og testes.

**Samlingen.** `Tiltaksdeltakelser` er wrapperen rundt alt vi mottok for en person, med narrowing (`girRett`, `ugyldige`, `medUkjentKildestatus`), overlappsvar som `Overlapp { Ja, Nei, Kanskje }`, og uttrekket `somKildenTilsierManKanSøkePå(påDato)` — en egen type som bærer datoen utvalget gjaldt.
`UkjentKildeverdi` samler alt kilden sa som vi ikke kjenner igjen — med `hva` («deltakerstatus fra Arena», «årsak fra Komet», …) og `kodeIKontrakten` — slik at varsling og visning slipper å kjenne hver akse.

**Hentingen.** `Tiltakshistorikk` er resultatet av én henting: deltakelsene, `UkjenteDeltakelsesformer` for kontraktsvarianter vi ikke har, og `hentetTidspunkt` ytterst.
Kompletthet bor på hentingen og ikke på `Tiltaksdeltakelser`, fordi samletypen også kan bygges fra lagrede rader.
`ukjenteKildeverdier` på hentingen samler alt som ikke lot seg tolke, på tvers av deltakelser og deltakelsesformer.

**Infrastrukturen logger ikke.**
`TiltakshistorikkHenter` returnerer i stedet nok til at konsumenten kan gjøre det, på samme måte som `httpklient`: `KunneIkkeHenteTiltakshistorikk` bærer feilen med metadata, og `TiltakshistorikkResultat` bærer `HttpKlientResponse` (som gir `loggSuksess` og rå respons) sammen med `Identoppslag`, som sier om oppslaget måtte falle tilbake til innsendt fnr.
Grunnen er at alvorligheten ikke er en egenskap ved hentingen, men ved hvem som venter på den: den samme feilen er en driftsfeil på den ekte veien og støy i en skyggekjøring.
Konsumenten har den konteksten, biblioteket har den ikke.

**Søknadsreglene er delt og forklarbare.**
`Søkbarhet` svarer om en deltakelse kan søkes på, med begrunnelse til visning — søknaden velger ut med dem, og saksbehandling-api viser dem, for eksempel ved manuell registrering.
Unntak aktiveres i regelsettet her, aldri lokalt hos én konsument, slik at flatene aldri kan divergere.

Det ene unntaket som finnes i dag er Arenas «ikke møtt».
Fag avklarte at det ikke er deltakelse og derfor ikke gir rett til innvilgelse, men ingen har sagt at bruker skal miste retten til å *søke* — og en feilregistrering i Arena er tung å få rettet.
Deltakelsen er derfor `KanSøkesPåVedUnntak`: den flyter inn i søknadsuttrekket, mens `Deltakerstatus` fortsatt sier `IkkeDeltatt`.
De to aksene skiller lag her med vilje, og det er nettopp derfor de er to.

## Regler som gjelder her

**Modulen svarer aldri på utfallet av en behandling.**
Om noe kan innvilges avhenger også av saksbehandlers vurdering, som libs ikke kjenner — derfor finnes ikke ordet `kanInnvilges` i modulen.
Alt som utledes av kildedata heter `somKildenTilsier…` eller `…FraKilden`.

**Ikke forgren på sealed-varianten for å avgjøre et utfall.**
Varianten beskriver datakvaliteten hos kilden.
Beslutningsstøtte skal ta verdiene den trenger (tiltakstype, periode, omfang), ikke aggregatet — ellers må en kaller med en vurdert periode enten fabrikkere en falsk kildeverdi eller duplisere logikken.

**Ingen prefiltrering.**
Alt kilden ga oss får en variant.
En ukjent tiltakskode eller datoer som ikke henger sammen forsvinner ikke i stillhet, de blir `UkjentTiltakstype` og `Ugyldig`.

**Domenetypene skal aldri lese fra eller skrive til en database.**
Konsumentene eier sine egne Db-typer og mapper til og fra dem.

'**Invariantene bor i typen, og en total funksjon etablerer dem før den konstruerer.**
Variantene håndhever sine egne påstander med `init` — en `UtenPeriode` med begge datoene på plass eller en `Ugyldig` med datoer som henger sammen kan ikke konstrueres.
Fabrikken `tiltaksdeltakelse(...)` er total nettopp fordi den bare kaller konstruktører hvis invarianter den allerede har etablert.

**Kildens tomme streng er fravær.**
Fritekst fra kilden går inn gjennom én total inngang per type — `virksomhetsnavn(...)` og `tilknytningstittel(...)` ved typene i `common` — der både `null` og blank gir `null`, aldri et kast.
`require`-ene i typene består som vakter mot programmererfeil; adaptere bruker inngangene og konstruerer aldri direkte fra fritekst.

## Kilder

Kontrakten vi mottar er `tiltakshistorikk` fra mulighetsrommet.
Hver `Kildestatus`-implementasjon har KDoc med lenker til både kontrakten og kildesystemet, og for Arena også til hvor kodene ble døpt om (`JATAKK` → `TAKKET_JA_TIL_TILBUD`).
