# pn-paper-tracker
TODO: Aggiungi una breve descrizione del progetto qui.

## Panoramica
Si compone di:
- Microservizio pn-paper-tracker : Aggiungi una descrizione del componente qui.

### Architettura
TODO: Aggiungi un diagramma dell'architettura qui.

### 
- **GET - &#x2F;status**: health check path per verificare lo stato del micro servizio- **POST - &#x2F;paper-tracker-private&#x2F;v1&#x2F;init**: Permette l&#39;inizializzazione di un&#39;entità di tracking.- **POST - &#x2F;paper-tracker-private&#x2F;v1&#x2F;trackings**: Permette il recupero delle entità di tracking da una lista di trackingId.- **POST - &#x2F;paper-tracker-private&#x2F;v1&#x2F;errors**: Permette il recupero degli errori presenti nella tabella PnPaperTrackingsErrors.- **POST - &#x2F;paper-tracker-private&#x2F;v1&#x2F;outputs**: Permette il recupero degli oggetti in output nella tabella PnPaperTrackerDryRunOutputs.- **GET - &#x2F;paper-tracker-private&#x2F;v1&#x2F;attempts&#x2F;{attemptId}**: Recupera il tracking della spedizione dato l&#39;attemptId.


## Componenti

### pn-paper-tracker

#### Responsabilità
- Legge e scrive sulle tabelle DynamoDB: PaperTrackingsTable, PaperTrackerDryRunOutputsTable, PaperTrackingsErrorsTable, PaperTrackingsTable, PaperTrackerDryRunOutputsTable, PaperTrackingsErrorsTable
- Legge e scrive sulle code SQS: ExternalChannelToPaperTrackerQueue, PnOcrOutputsQueue, PnOcrInputsQueue, ExternalChannelsOutputsQueue

#### Configurazione
| Variabile Ambiente | Descrizione                                                             | Default | Obbligatorio |
|--------------------|-------------------------------------------------------------------------|---------|--------------|
| AWS_REGIONCODE      | AWS Region Code                                                         | -       | Si           |
| PN_CRON_ANALYZER    | Cron for which you send the metric to CloudWatch                        | -       | No           |
| WIRE_TAP_LOG        | Activation of wire logs                                                 | -       | No           |
| PN_PAPERTRACKER_DAO_PAPERTRACKERDRYRUNOUTPUTSTABLE    | DynamoDB table name for PaperTrackerDryRunOutputs                       | -       | Si           |
| PN_PAPERTRACKER_DAO_PAPERTRACKINGSERRORSTABLE    | DynamoDB table name for PaperTrackingsErrors                            | -       | Si           |
| PN_PAPERTRACKER_DAO_PAPERTRACKINGSTABLE    | DynamoDB table name for PaperTrackings                                  | -       | Si           |
| PN_PAPERTRACKER_PAPERCHANNELBASEURL    | Base url for paper-channel APIs                                         | -       | Si           |
| PN_PAPERTRACKER_PAPERTRACKINGSTTLDURATION    | DynamoDB PaperTrackings entity TTL duration                             | -       | Sì           |
| PN_PAPERTRACKER_TOPICS_EXTERNALCHANNELTOPAPERTRACKER    | Name of the SQS queue where external channel messages are sent          | -       | Si           |
| PN_PAPERTRACKER_QUEUEOCRINPUTSURL    | URL of the SQS queue where OCR inputs are sent                          | -       | Si           |
| PN_PAPERTRACKER_QUEUEOCRINPUTSREGION    | Region of the SQS queue where OCR inputs are sent                       | -       | Si           |
| PN_PAPERTRACKER_EXTERNALCHANNELOUTPUTSQUEUE    | Name of the SQS queue where external channel outputs are sent           | -       | Si           |
| PN_PAPERTRACKER_COMPIUTAGIACENZAARDURATION    | Duration for compiuta giacenza (e.g., 5d, 5h, 5m, 5s)                   | -       | Si           |
| PN_PAPERTRACKER_ENABLETRUNCATEDDATEFORREFINEMENTCHECK    | If enabled truncate datetime to local date for refinement check         | -       | Sì           |
| PN_PAPERTRACKER_REFINEMENTDURATION    | Duration for refinement                                                 | -       | Si           |
| PN_PAPERTRACKER_SAFESTORAGEBASEURL    | URL to the SafeStorage microservice                                     | -       | Si           |
| PN_PAPERTRACKER_SAFESTORAGECXID    | CxId for the SafeStorage microservice                                   | -       | Si           |
| PN_PAPERTRACKER_TOPICS_PNOCROUTPUTS    | Name of the SQS queue where OCR outputs are sent                        | -       | Si           |
| PN_PAPERTRACKER_ENABLEOCRVALIDATION    | Feature flag for enabling OCR validation                                | -       | Sì           |

## Testing in locale

### Prerequisiti
1. Docker/Podman avviato con container di Localstack (puoi utilizzare il Docker Compose di [Localdev] https://github.com/pagopa/pn-localdev)
...

I dettagli sui test di integrazione e le procedure di testing sono disponibili in [README_TEST.md](./README_TEST.md).