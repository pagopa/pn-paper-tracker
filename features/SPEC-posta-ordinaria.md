# Spec: Posta Ordinaria (PO)

## Stato e approvazioni

- Stato: proposta da approvare.
- Data: 2026-11-10.
- Ambito: introduzione di un solo prodotto postale nel flusso di tracking esistente.
- Questo documento non costituisce un piano di implementazione.
- Nessuna modifica applicativa, di configurazione o infrastrutturale e' autorizzata
  da questa fase. Dopo l'approvazione della specifica si potra' produrre il piano;
  anche il piano e le successive attivita' richiederanno approvazione prima dello
  sviluppo.
- I punti aperti sono espliciti: l'approvazione della direzione proposta non
  equivale all'approvazione di regole ancora da definire.

## Obiettivo

Consentire a pn-paper-channel di inizializzare una spedizione di Posta Ordinaria
e a pn-paper-tracker di riceverne gli eventi attraverso le interfacce esistenti.
Dopo la presa in carico del recapitista, il prodotto prevede un solo evento
postale: la consegna.

Il risultato atteso e' la conservazione del tracking e la produzione dell'esito
finale verso il destinatario configurato, senza alterare AR, RIR, 890, RS e RIS.

### Decisioni confermate dal richiedente

1. `deliveryType=PO` nel dominio chiamante corrisponde a `productType=PO`
   nell'API di inizializzazione e negli eventi del tracker. Non si introduce un
   campo `deliveryType` nel tracker.
2. Il codice di consegna deve essere proposto ex novo, usando un codice `REC`
   non gia' utilizzato.
3. Restano gestiti gli eventi tecnici comuni gia' supportati; non si restringe
   l'intero flusso ai soli eventi di presa in carico e consegna.
4. Allegati, OCR e obbligatorieta' di `registeredLetterCode` sono da definire.

## Evidenze del repository

| Superficie | Comportamento attuale e implicazione |
|---|---|
| [Contratto interno](../docs/openapi/api-internal-v1.yaml) | L'inizializzazione usa `TrackingCreationRequest`; `ProductType` e' una stringa, non un enum OpenAPI chiuso. |
| [ProductType](../src/main/java/it/pagopa/pn/papertracker/middleware/dao/dynamo/entity/ProductType.java) | L'enum applicativo non comprende PO; `fromValue` rifiuta i prodotti sconosciuti. |
| [PaperTrackingsMapper](../src/main/java/it/pagopa/pn/papertracker/mapper/PaperTrackingsMapper.java) | L'init salva `AWAITING_REFINEMENT` / `AWAITING_FINAL_STATUS_CODE`, configura processing mode e OCR per prodotto e costruisce `trackingId=attemptId.pcRetry`. |
| [EventStatusCodeEnum](../src/main/java/it/pagopa/pn/papertracker/model/EventStatusCodeEnum.java) | Il codice determina prodotto, tipo evento ed esito. `CON018` e' intermedio; `REC018` e' save-only. Entrambi descrivono l'accettazione del recapitista. |
| [ExternalChannelHandler](../src/main/java/it/pagopa/pn/papertracker/middleware/queue/consumer/internal/ExternalChannelHandler.java) | Gli eventi comuni `ALL` sono instradati usando `productType` del payload; gli eventi specifici usano il prodotto associato al codice. |
| [HandlersRegistry](../src/main/java/it/pagopa/pn/papertracker/service/handler_step/generic/HandlersRegistry.java) | Registra le factory per prodotto. PO deve avere una gestione coerente sia per gli eventi comuni sia per quello finale. |
| [AbstractHandlersFactory](../src/main/java/it/pagopa/pn/papertracker/service/handler_step/generic/AbstractHandlersFactory.java) | La pipeline finale standard include validazione sequenza, dematerializzazione e trigger M10: non e' automaticamente adatta a PO. |
| [GenericSequenceValidator](../src/main/java/it/pagopa/pn/papertracker/service/handler_step/generic/GenericSequenceValidator.java) | Verifica anche presenza/coerenza del codice raccomandata e documenti. Il riuso integrale imporrebbe vincoli non ancora concordati per PO. |
| [OutputTargetSender](../src/main/java/it/pagopa/pn/papertracker/service/handler_step/generic/OutputTargetSender.java) | Riusa output DRY/RUN e imposta `DONE` dopo la gestione dell'output finale; gli status filtrati dalla configurazione non vengono inviati. |

Non risultano sistemi di specifica preesistenti nelle directory esaminate.
La proposta resta un unico documento: inizializzazione, eventi e chiusura sono
parti della stessa capacita', non prodotti indipendenti.

## Contratto proposto

### Inizializzazione

- Endpoint invariato: `POST /paper-tracker-private/v1/init`.
- Stesso payload, header e regole sugli identificativi degli altri prodotti,
  con `productType: "PO"`.
- Stessa persistenza e stessa semantica di `putIfAbsent`: nessuna nuova chiave,
  tabella o migrazione dei tracking esistenti.
- Consultazione dei tracking tramite le API esistenti, preservando `PO`.
- Aggiornare le descrizioni dei prodotti nei contratti pertinenti, senza
  introdurre un enum chiuso dove oggi e' ammessa una stringa.

### Eventi e codice di consegna

Proposta di codifica:

| Campo | Valore proposto |
|---|---|
| `productType` | `PO` |
| `statusCode` in ingresso | `RECPO001` |
| Descrizione | `Consegnato` |
| Classificazione interna | `FINAL_EVENT` |
| Esito interno / `statusCode` in output | `OK` |
| `statusDetail` in output | `RECPO001` |
| Dematerializzazione | Non classificato come evento di dematerializzazione |

`RECPO001` non compare nelle sorgenti e nei contratti locali esaminati alla data
di questa proposta. La verifica locale non garantisce l'assenza del codice negli
altri servizi o nei cataloghi del consolidatore: la codifica deve essere
concordata con pn-external-channel, pn-paper-channel e i consumatori dell'esito.
Non si riutilizza un codice di un altro prodotto e non si introducono varianti
`A/B/C` per pre-esito, dematerializzazione e chiusura fascicolo.

Gli eventi tecnici comuni mantengono le classificazioni esistenti, comprese le
distinzioni fra progress, save-only e scarti. Non si aggiungono esiti postali
negativi, giacenze o retry specifici PO. Il riuso degli eventi tecnici comprende
le relative gestioni di errore/retry gia' previste: non e' una garanzia di
consegna per ogni spedizione.

### Macchina a stati proposta

| Fase / input | Stato tecnico | Stato business | Effetto |
|---|---|---|---|
| Init PO | `AWAITING_REFINEMENT` | `AWAITING_FINAL_STATUS_CODE` | Tracking inizializzato. |
| Eventi tecnici ordinari, inclusa accettazione | Invariato rispetto al flusso comune | Invariato rispetto al flusso comune | Persistenza ed eventuale progress secondo le regole esistenti. |
| `RECPO001` valido e output completato | `DONE` | `DONE` | Esito `OK`, `paperStatus.finalStatusCode=RECPO001`. |
| Evento non valido / errore tecnico | Secondo la gestione esistente applicabile | Secondo la gestione esistente applicabile | Errore esplicito; nessuna chiusura positiva fittizia. |

La presa in carico e' una fase logica: non si propone un nuovo stato persistito.
Occorre decidere se la presenza di `CON018`, `REC018` o di un altro codice sia
un prerequisito bloccante della consegna. Non si presume che questi due eventi
siano equivalenti per una nuova validazione di sequenza.

## Proposta tecnica e motivazioni

- Estendere il riconoscimento del prodotto e il catalogo eventi con PO e
  `RECPO001`; integrare la gestione PO nel registro di handler esistente.
- Riutilizzare persistenza, controlli di corrispondenza prodotto, controlli di
  stato, gestione dei duplicati e invio dell'output dove compatibili.
- Prevedere una pipeline finale PO dedicata nella composizione degli step,
  anziche' copiare la pipeline delle raccomandate o rilassarne globalmente i
  controlli. La scelta delle classi e degli step appartiene al successivo piano.
- Non confondere assenza di triplette con assenza di validazione: restano da
  verificare correlazione al tracking, prodotto, timestamp e condizioni di
  chiusura concordate.
- Proposta da valutare, non decisione acquisita: nessun allegato obbligatorio,
  nessun OCR e `registeredLetterCode` facoltativo per PO. Se il dominio richiede
  documenti o codice di tracciatura, adeguare questa specifica prima del piano.
- Conservare la semantica esistente di riaccodamento, redrive e rework senza
  promettere exactly-once. Testare separatamente duplicati di business e
  riconsegne tecniche dello stesso messaggio.
- Configurare esplicitamente la modalita' PO tramite i meccanismi per prodotto
  esistenti. La scelta DRY/RUN e la compatibilita' del percorso verso
  pn-paper-channel devono precedere l'attivazione: DRY non e' automaticamente
  sicuro se il servizio destinatario non supporta ancora PO.
- Preservare logging/errori standard con trackingId, prodotto e statusCode;
  verificare la visibilita' di PO nelle metriche e negli allarmi esistenti.
  Nuovi allarmi o modifiche infrastrutturali richiedono approvazione separata.

### Alternative non proposte

- Equiparare PO a RS: confonderebbe prodotti diversi e importerebbe regole di
  documenti, esiti e sequenze non richieste.
- Modificare la validazione generica per tutti i prodotti: allargherebbe il
  rischio di regressione senza un requisito.
- Creare un endpoint o un modello di persistenza separato: non necessario per
  il flusso richiesto.

## Tech stack

Stack esistente: Java 21, Spring Boot 3.x/WebFlux e Reactor, Maven, DynamoDB,
SQS ed EventBridge. Test con JUnit Jupiter, Mockito, reactor-test e
Testcontainers/LocalStack.

Versioni dichiarate nel [pom.xml](../pom.xml): parent pn-parent
`2.3.1-SPRINGBOOT3`, pn-commons `2.18.0-SPRINGBOOT3`, pn-model `2.11.0`,
OpenAPI Generator `7.13.0`, Testcontainers LocalStack `1.20.6`.
Non si propongono dipendenze aggiuntive o aggiornamenti di versione.

## Comandi

Comandi dalla root, da eseguire nella futura fase di sviluppo:

| Scopo | Comando |
|---|---|
| Compilazione e generazione modelli | `mvn -B -DskipTests package` |
| Unit test mirati sulle superfici esistenti | `mvn -B -Dtest=PaperTrackingsMapperTest,PaperTrackerTrackingServiceImplTest,ExternalChannelHandlerTest,CheckTrackingProductTest,CheckTrackingStateTest,OutputTargetSenderTest test` |
| Suite unitaria | `mvn -B test` |
| Scenari IT attuali selezionati esplicitamente | `mvn -B '-Dtest=DryRSOcrDisableTestIT,DryRISOcrDisableTestIT,DryRIROcrDisableTestIT' test` |
| Verifica Maven | `mvn -B verify` |
| Avvio locale documentato | `mvn spring-boot:run` |

I test PO e i relativi selettori saranno definiti nel piano. Gli IT richiedono
Docker/Podman e LocalStack; build/generazione richiedono accesso alle dipendenze
e ai contratti remoti gia' referenziati dal POM. Non e' stato accertato che
`verify` includa tutti i test con suffisso IT: usare selettori espliciti.
Non e' definito nel POM locale un comando lint autonomo ne' una soglia numerica
di coverage; non introdurne o inventarne una in questa proposta.
Nessuno di questi comandi e' stato eseguito per questa modifica solo documentale.

## Struttura di progetto

- `src/main/java/it/pagopa/pn/papertracker/`: sorgenti applicative.
  - `middleware/dao/dynamo/entity/`: prodotti, stati e dati persistiti.
  - `model/` e `model/sequence/`: cataloghi e regole degli eventi.
  - `service/handler_step/`: composizione e validazione del flusso.
  - `mapper/`, `config/`, `utils/`: mapping, configurazione e supporto condiviso.
- `src/test/java/it/pagopa/pn/papertracker/`: test unitari e scenari `it/`.
- `src/test/resources/testcase/`: fixture JSON per prodotto.
- `docs/openapi/`: contratti sorgente; modelli generati da Maven, non da editare.
- `config/` e `scripts/aws/cfn/`: configurazioni locali e di deployment.
- `README.md` e `docs/ms/ArchitetturaInterna.md`: documentazione da aggiornare
  insieme alla futura implementazione, senza dichiarare PO gia' disponibile.

## Stile del codice

Seguire Java con quattro spazi, classi PascalCase, metodi/variabili camelCase,
enum UPPER_SNAKE_CASE, constructor injection e pipeline Reactor. Usare i tipi
ed errori del repository; non aggiungere `.block()` agli step reattivi.

Esempio reale dal servizio di inizializzazione esistente:

```java
@Override
public Mono<Void> insertPaperTrackings(TrackingCreationRequest trackingCreationRequest, String xOriginClientId) {
    log.info("Init tracking for request: {}", trackingCreationRequest);

    return paperTrackingsDAO.putIfAbsent(toPaperTrackings(trackingCreationRequest, trackerConfigUtils, pnPaperTrackerConfigs, Instant.now(), xOriginClientId)).then();
}
```

Non introdurre framework alternativi o astrazioni generiche per un solo prodotto.

## Strategia di test

- Unit test JUnit/Mockito e StepVerifier per riconoscimento PO, classificazione
  evento, dispatch, mapping init/output, configurazioni e chiusura.
- Verificare che un evento PO non chiuda un tracking di un altro prodotto e
  viceversa; gli eventi tecnici `ALL` devono funzionare anche con PO.
- Casi negativi per tracking non inizializzato, stato incompatibile, timestamp
  incoerenti e dati mancanti secondo le regole che saranno approvate.
- Duplicati prima/dopo chiusura, riconsegna dello stesso messaggio, redrive e
  rework: preservare le regole esistenti, senza aggiungere una nuova finalizzazione
  postale per effetto di un normale duplicato dopo il completamento.
- Scenari con fixture PO nel runner di integrazione esistente: init, eventi
  tecnici, accettazione, consegna e recupero tracking; output DRY e RUN.
- Verificare separatamente errori di pubblicazione: non salvare `DONE` come
  conseguenza di un invio fallito.
- Dopo la decisione sui metadati, aggiungere test con/senza codice di tracciatura
  e con/senza allegati, verificando esattamente quando OCR sia atteso o escluso
  e quanti output vengano prodotti.
- Eseguire regressioni per AR, RIR, 890, RS e RIS. Nessun test esistente saltato,
  rimosso o indebolito per fare passare PO; mantenere i quality gate del parent.

## Confini

### Sempre

- Preservare contratti, identificativi e comportamento degli altri prodotti.
- Riutilizzare gestione errori e protezione dei dati personali esistenti.
- Aggiornare specifica e documentazione quando una decisione cambia.
- Verificare i percorsi init, ingresso eventi, persistenza, output e configurazione.

### Chiedere prima

- Risolvere le domande aperte e approvare specifica, piano e attivita' successive.
- Aggiungere dipendenze, cambiare schema DB, CI o infrastruttura.
- Cambiare semantica di errori, duplicati, rework o retry comuni.
- Attivare PO in un ambiente o modificare repository/contratti di altri servizi.

### Mai

- Inventare eventi ricevuti per completare una sequenza.
- Disabilitare controlli delle raccomandate per consentire PO.
- Nascondere errori o trasformare fallimenti tecnici in una consegna positiva.
- Inserire credenziali, indirizzi reali o altri dati personali nelle fixture.
- Eseguire commit, push o sviluppo applicativo senza richiesta e approvazioni.

## Criteri di successo

1. L'init con `productType=PO` persiste il tracking con gli stati iniziali
   esistenti e lo rende consultabile senza cambiare il contratto.
2. Gli eventi tecnici comuni mantengono il comportamento previsto per la
   modalita' configurata, senza errori di prodotto/handler sconosciuto per PO.
3. La consegna `RECPO001` e' riconosciuta solo per PO, produce l'esito `OK` con
   dettaglio `RECPO001` e, dopo output completato, tracking e business state
   risultano `DONE` con finalStatusCode coerente.
4. Non sono richiesti ulteriori eventi postali di pre-esito o fascicolo chiuso.
5. Prerequisiti di accettazione, metadati, documenti e OCR sono coperti da
   condizioni positive e negative dopo la risoluzione dei punti aperti.
6. Eventi non validi, duplicati e fallimenti di output sono gestiti esplicitamente
   secondo le regole approvate, senza nuovi esiti positivi impropri.
7. Test PO, regressioni dei cinque prodotti esistenti e build sono superati.
8. Codifica e mapping sono concordati con le integrazioni esterne prima
   dell'attivazione; configurazioni e documentazione descrivono il supporto PO.

## Domande aperte e gate per il piano

| ID | Decisione necessaria | Proposta / vincolo |
|---|---|---|
| Q1 | Approvazione e disponibilita' esterna del codice `RECPO001`. | Non utilizzato localmente; conferma esterna necessaria prima dell'attivazione. |
| Q2 | Quale evento rappresenta la presa in carico e deve precedere obbligatoriamente la consegna? | Valutare `CON018` / `REC018`; definire anche il trattamento di eventi fuori ordine e la relazione fra timestamp. Nessun prerequisito implicito. |
| Q3 | Obbligatorieta' e coerenza di `registeredLetterCode`. | Da definire dal richiedente; proposta facoltativo, non ancora approvata. |
| Q4 | Allegati ammessi/richiesti, OCR e cardinalita' dell'output finale. | Da definire dal richiedente; proposta niente allegati obbligatori/OCR. L'attuale mapper puo' produrre piu' messaggi in presenza di allegati: non assumere un solo messaggio per il solo fatto che esiste un unico evento postale. |
| Q5 | Modalita' iniziale PO e compatibilita' dei servizi collegati. | Scegliere DRY/RUN e configurazione con responsabilita' esplicite; non assumere supporto PO in pn-paper-channel o nei consumatori. |

Q2-Q4 determinano le regole finali e devono essere risolte prima di approvare
un piano eseguibile; Q5 deve definire almeno il perimetro di verifica e rollout.
Si puo' approvare la direzione generale lasciando tali punti aperti, ma non
avviare lo sviluppo sulla base di assunzioni non confermate.
