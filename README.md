# pn-paper-tracker

## Indice
- [Descrizione](#descrizione)
- [Tecnologie Utilizzate](#tecnologie-utilizzate)
- [Architettura](#architettura)
- [Interfacce del Servizio](#interfacce-del-servizio)
- [Allarmi e monitoraggio](#allarmi-e-monitoraggio)
- [Configurazione](#configurazione)
- [Esecuzione](#esecuzione)

## Descrizione
**pn-paper-tracker** è il microservizio responsabile della validazione e tracciamento delle spedizioni analogiche (cartacee) all'interno del sistema **[SEND](https://notifichedigitali.it/)**.

### Responsabilità principali

* **Ricezione eventi:** Riceve gli aggiornamenti di stato delle spedizioni dal consolidatore tramite **pn-external-channel**
* **Validazione triplette:** Valida la coerenza degli eventi ricevuti (pre-esito, dematerializzazione, fascicolo chiuso) verificando:
  * Presenza di tutti gli eventi richiesti per ogni flusso
  * Coerenza dei timestamp di business
  * Correttezza dei metadati (`registeredLetterCode`, `deliveryFailureCause`, ecc.)
* **Validazione OCR/AI:** Integra il servizio OCR per la verifica dei documenti allegati scansionati
* **Tracciamento spedizioni:** Mantiene lo stato completo di ogni tentativo di spedizione verso un destinatario
* **Inoltro eventi:** Produce eventi validati verso **pn-delivery-push** per l'aggiornamento della timeline notifica

### Prodotti postali gestiti
* AR (Raccomandata con Ricevuta di Ritorno)
* RIR (Raccomandata Internazionale con Ricevuta di Ritorno)
* 890 (Atti Giudiziari)
* RS, RIS (Raccomandata Semplice, Raccomandati Internazionale Semplice)

### Contesto storico
Il servizio è stato introdotto per separare le responsabilità di validazione e tracciamento dal microservizio **pn-paper-channel**, che ora gestisce solo le fasi iniziali (PREPARE e SEND).
Questa riorganizzazione permette di:
* Ridurre la complessità di pn-paper-channel
* Migliorare la manutenibilità
* Introdurre nuove validazioni OCR

## Tecnologie Utilizzate
### Stack Tecnologico
* **Java 21** con **Spring Boot 3.x** e **WebFlux**
* **AWS SDK v2** per integrazione servizi AWS
* **OpenAPI 3.0** per definizione contratti API
* **AsyncAPI 3.0** per definizione DTO code
* **Maven** per build management

### Servizi utilizzati
* **Amazon DynamoDB:** DB principale per tracciamento spedizioni
* **Amazon SQS:** messaggistica asincrona
* **Amazon EventBridge:** bus eventi per pubblicazione degli eventi validati

## Architettura

Il microservizio pn-paper-tracker implementa una logica di business articolata per la gestione degli eventi provenienti dal consolidatore.
Per una descrizione dettagliata dei flussi, delle classi principali e delle sequenze di eventi, 
è possibile consultare il file [ArchitetturaInterna.md](docs/ms/ArchitetturaInterna.md).

### Dipendenze interne

- **pn-paper-channel:** per la richiesta di retry nel caso di eventi retryable
- **pn-data-vault:** per l'anonimizzazione/deanonimizzazione dei dati sensibili

### Dipendenze esterne

- **servizio OCR:** per la validazione dei documenti allegati alla spedizione

## Interfacce del Servizio

| Tipo  | Dir | Risorsa                                     | Protocollo  | Metodo  | Route / Destinazione                                                    | Descrizione                                            |
|-------|-----|---------------------------------------------|-------------|---------|-------------------------------------------------------------------------|--------------------------------------------------------|
| API   | IN  | pn-paper-tracker                            | REST        | POST    | /paper-tracker-private/v1/init                                          | Inizializzazione entità di tracking                    |
| API   | IN  | pn-paper-tracker                            | REST        | POST    | /paper-tracker-private/v1/trackings                                     | Recupero entità di tracking da lista di trackingId     |
| API   | IN  | pn-paper-tracker                            | REST        | POST    | /paper-tracker-private/v1/errors                                        | Recupero errori di tracking                            |
| API   | IN  | pn-paper-tracker                            | REST        | POST    | /paper-tracker-private/v1/outputs                                       | Recupero degli oggetti in output                       |
| API   | IN  | pn-paper-tracker                            | REST        | GET     | /paper-tracker-private/v1/attempts/{attemptId}                          | Recupero tracking tramite attemptId                    |
| API   | IN  | pn-paper-tracker                            | REST        | GET     | /paper-tracker-private/v1/notification-rework/sequence                  | Recupero sequence e finalStatus per rework notifica    |
| API   | IN  | pn-paper-tracker                            | REST        | PUT     | /paper-tracker-private/v1/notification-rework/{trackingId}/init         | Avvio processo di invalidazione timeline per rework    |
| API   | OUT | pn-paper-channel                            | REST        | GET     | /paper-channel-private/v1/b2b/pc-retry/{requestId}                      | Verifica se ci sono altri retry                        |
| API   | OUT | pn-data-vault                               | REST        | PUT     | /datavault-private/v1/paper-addresses/{paperRequestId}/{paperAddressId} | Inserisci o modifica un indirizzo                      |
| API   | OUT | pn-data-vault                               | REST        | GET     | /datavault-private/v1/paper-addresses/{paperRequestId}/{paperAddressId} | Recupera tutti gli indirizzi associati alla spedizione |
| EVENT | IN  | pn-ocr_outputs                              | SQS         | CONSUME | -                                                                       | Risposta validazione OCR degli allegati spedizione     |
| EVENT | OUT | send-receipt-validation-input               | SQS         | PRODUCE | -                                                                       | Invio allegati di spedizione all'OCR per validazione   |
| EVENT | IN  | pn-external_channel_to_paper_channel        | SQS         | CONSUME | -                                                                       | Ricezione eventi dal consolidatore                     |
| EVENT | IN  | pn-external_channel_to_paper_tracker        | SQS         | CONSUME | -                                                                       | Ricezione eventi smistati da pn-paper-tracker          |
| EVENT | OUT | pn-external_channel_to_paper_channel_dryrun | SQS         | PRODUCE | -                                                                       | Produzione eventi smistati verso pn-paper-channel      |
| EVENT | OUT | pn-CoreEventBus                             | EventBridge | PUBLISH | -                                                                       | Pubblicazione eventi su EventBridge                    |
| API   | OUT | pn-safe-storage                             | REST        | GET     | /safe-storage-private/v1/files/{fileKey}                                | Recupero file allegati alla spedizione                 |
| EVENT | OUT | pn-external_channel_to_paper_tracker        | SQS         | PRODUCE | -                                                                       | Routing eventi verso pn-paper-tracker in modalità RUN e DRY |

* **OpenAPI**: [api-internal-v1.yaml](docs/openapi/api-internal-v1.yaml)
* **AsyncAPI**: [internal-datalake-v1.yaml](docs/asyncapi/internal-datalake-v1.yaml), [external-datalake-v1.yaml](docs/asyncapi/external-datalake-v1.yaml)

## Allarmi e monitoraggio

| Tipo      | Nome                             | Descrizione                                                                                                    |
|-----------|----------------------------------|----------------------------------------------------------------------------------------------------------------|
| ALARM     | pn-paper-tracker-890Errors-Alarm | Allarme su errori di tipo 890 (Atti Giudiziari). Scatta se il numero di errori supera la soglia configurata.   |
| ALARM     | pn-paper-tracker-RISErrors-Alarm | Allarme su errori di tipo RIS (Raccomandata Internazionale Semplice).                                          |
| ALARM     | pn-paper-tracker-ARErrors-Alarm  | Allarme su errori di tipo AR (Raccomandata con Ricevuta di Ritorno).                                           |
| ALARM     | pn-paper-tracker-RSErrors-Alarm  | Allarme su errori di tipo RS (Raccomandata Semplice).                                                          |
| ALARM     | pn-paper-tracker-RIRErrors-Alarm | Allarme su errori di tipo RIR (Raccomandata Internazionale con Ricevuta di Ritorno).                           |
| LOG       | /aws/ecs/pn-paper-tracker        | Log applicativi ECS del microservizio, consultabili su CloudWatch Logs.                                        |

**Note operative:**
- Le metriche monitorate includono il conteggio degli errori per categoria di prodotto postale (890, AR, RS, RIS, RIR).

Per consultare lo stato operativo del servizio, accedere alla dashboard CloudWatch `pn-paper-tracker` e al log group `/aws/ecs/pn-paper-tracker`.

## Configurazione

Le principali configurazioni del microservizio sono gestite tramite variabili d'ambiente:

| Nome                                                                                                                                                                                      | Sorgente | Valori | Descrizione                                                                              |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|--------|------------------------------------------------------------------------------------------|
| [PN_PAPERTRACKER_ENABLEOCRVALIDATIONFOR](https://github.com/pagopa/pn-paper-tracker/blob/ca6886a5053248cfcfe4635734d3ebb50197d2d3/scripts/aws/cfn/application-dev.env#L4)                 | ENV      | -      | Abilita l'OCR per singolo prodotto                                                       |
| [PN_PAPERTRACKER_OCRFILTERTEMPORAL](https://github.com/pagopa/pn-paper-tracker/blob/ca6886a5053248cfcfe4635734d3ebb50197d2d3/scripts/aws/cfn/application-dev.env#L8)                      | ENV      | -      | Abilita l'OCR in modalità RUN per spedizioni in specifiche fasce orarie                  |
| [PN_PAPERTRACKER_OCRFILTERUNIFIEDDELIVERYDRIVER](https://github.com/pagopa/pn-paper-tracker/blob/ca6886a5053248cfcfe4635734d3ebb50197d2d3/scripts/aws/cfn/application-dev.env#L13)        | ENV      | -      | Abilita l'OCR in modalità RUN per determinati recapitisti                                |
| [PN_PAPERTRACKER_ENABLEOCRVALIDATIONFORFILE](https://github.com/pagopa/pn-paper-tracker/blob/ca6886a5053248cfcfe4635734d3ebb50197d2d3/scripts/aws/cfn/application-dev.env#L15)            | ENV      | -      | Estensione file abilitata per la validazione OCR                                         |
| [PN_PAPERTRACKER_SAVEANDNOTSENDTODELIVERYPUSH](https://github.com/pagopa/pn-paper-tracker/blob/ca6886a5053248cfcfe4635734d3ebb50197d2d3/scripts/aws/cfn/application-dev.env#L20)          | ENV      | -      | StatusCode che non invia a pn-delivery-push ma vengono salvati nel tracking              |
| [PN_PAPERTRACKER_REQUIREDATTACHMENTSREFINEMENTSTOCK890](https://github.com/pagopa/pn-paper-tracker/blob/ca6886a5053248cfcfe4635734d3ebb50197d2d3/scripts/aws/cfn/application-dev.env#L42) | ENV      | -      | Allegati necessari al perfezionamento giacenza 890                                       |
| [PN_PAPERTRACKER_SENDOCRATTACHMENTSFINALVALIDATION](https://github.com/pagopa/pn-paper-tracker/blob/ca6886a5053248cfcfe4635734d3ebb50197d2d3/scripts/aws/cfn/application-dev.env#L60)     | ENV      | -      | Allegati da inviare all'OCR per la validazione finale                                    |
| [PN_PAPERTRACKER_PRODUCTSPROCESSINGMODES](https://github.com/pagopa/pn-paper-tracker/blob/ca6886a5053248cfcfe4635734d3ebb50197d2d3/scripts/aws/cfn/application-dev.env#L85)               | ENV      | -      | Modalità di processamento per prodotto                                                   |
| [PN_PAPERTRACKER_COMPIUTAGIACENZAARDURATION](https://github.com/pagopa/pn-paper-tracker/blob/06db37cf0b0faa61ff38b557e51854830396d43b/scripts/aws/cfn/application-dev.env#L23)              | ENV      | -      | Durata della giacenza compiuta AR                                                        |
| [PN_PAPERTRACKER_ENABLETRUNCATEDDATEFORREFINEMENTCHECK](https://github.com/pagopa/pn-paper-tracker/blob/06db37cf0b0faa61ff38b557e51854830396d43b/scripts/aws/cfn/application-dev.env#L26)   | ENV      | -      | Abilita troncamento data per controllo perfezionamento                                   |
| [PN_PAPERTRACKER_REFINEMENTDURATION](https://github.com/pagopa/pn-paper-tracker/blob/06db37cf0b0faa61ff38b557e51854830396d43b/scripts/aws/cfn/application-dev.env#L28)                      | ENV      | -      | Durata del perfezionamento                                                               |
| [PN_PAPERTRACKER_PAPERTRACKINGSTTLDURATION](https://github.com/pagopa/pn-paper-tracker/blob/06db37cf0b0faa61ff38b557e51854830396d43b/scripts/aws/cfn/application-dev.env#L31)               | ENV      | -      | TTL delle entità di tracking                                                             |
| [PN_PAPERTRACKER_PAPERTRACKINGSERRORSTTLDURATION](https://github.com/pagopa/pn-paper-tracker/blob/06db37cf0b0faa61ff38b557e51854830396d43b/scripts/aws/cfn/application-dev.env#L34)         | ENV      | -      | TTL degli errori di tracking                                                             |
| [PN_PAPERTRACKER_MAXPCRETRYMOCK](https://github.com/pagopa/pn-paper-tracker/blob/06db37cf0b0faa61ff38b557e51854830396d43b/scripts/aws/cfn/application-dev.env#L37)                          | ENV      | -      | Numero massimo di retry mock per paper-channel                                           |
| [PN_PAPERTRACKER_SENDOCRATTACHMENTSREFINEMENTSTOCK890](https://github.com/pagopa/pn-paper-tracker/blob/06db37cf0b0faa61ff38b557e51854830396d43b/scripts/aws/cfn/application-dev.env#L48)    | ENV      | -      | Allegati da inviare all'OCR per il perfezionamento giacenza 890                          |
| [PN_PAPERTRACKER_STRICTFINALVALIDATIONSTOCK890](https://github.com/pagopa/pn-paper-tracker/blob/06db37cf0b0faa61ff38b557e51854830396d43b/scripts/aws/cfn/application-dev.env#L70)           | ENV      | -      | Abilita validazione finale strict per 890                                                |
| [PN_PAPERTRACKER_STRICTDELIVERYFAILURECAUSE](https://github.com/pagopa/pn-paper-tracker/blob/06db37cf0b0faa61ff38b557e51854830396d43b/scripts/aws/cfn/application-dev.env#L76)              | ENV      | -      | Abilita validazione strict su deliveryFailureCause                                       |
| [PN_PAPERTRACKER_INTERNALEVENTS](https://github.com/pagopa/pn-paper-tracker/blob/06db37cf0b0faa61ff38b557e51854830396d43b/scripts/aws/cfn/application-dev.env#L80)                          | ENV      | -      | StatusCode gestiti internamente senza inoltro                                            |
| [PN_PAPERTRACKER_REDRIVEENABLEDDOMAINS](https://github.com/pagopa/pn-paper-tracker/blob/06db37cf0b0faa61ff38b557e51854830396d43b/scripts/aws/cfn/application-dev.env#L89)                   | ENV      | -      | Domini abilitati al redrive degli eventi                                                 |

Per l'elenco completo e i dettagli di tutte le variabili, è possibile consultare il file [application-dev.env](scripts/aws/cfn/application-dev.env).

## Esecuzione
**Prerequisiti**
* Docker/Podman avviato

Eseguire il comando Maven:
```bash
mvn spring-boot:run
```
