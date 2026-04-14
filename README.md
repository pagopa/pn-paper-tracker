# pn-paper-tracker

## Indice
- [Descrizione](#descrizione)
- [Tecnologie Utilizzate](#tecnologie-utilizzate)
- [Architettura](#architettura)
- [API & Documentazione](#api--documentazione)
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

## Architettura

Il microservizio pn-paper-tracker implementa una logica di business articolata per la gestione degli eventi provenienti dal consolidatore.
Per una descrizione dettagliata dei flussi, delle classi principali e delle sequenze di eventi, 
è possibile consultare il file [ArchitetturaInterna.md](docs/ms/ArchitetturaInterna.md).

### Dipendenze interne

- **pn-paper-channel:** per la richiesta di retry nel caso di eventi retryable
- **pn-data-vault:** per l'anonimizzazione/deanonimizzazione dei dati sensibili

### Dipendenze esterne

- **servizio OCR:** per la validazione dei documenti allegati alla spedizione

## API & Documentazione

- Documentazione OpenAPI/Swagger disponibile in [api-internal-v1.yaml](docs/openapi/api-internal-v1.yaml)

## Allarmi e monitoraggio

## Configurazione

Le principali configurazioni del microservizio sono gestite tramite variabili d'ambiente:

| Nome                                   | Valori                                             | Descrizione                                                                              |
|----------------------------------------|----------------------------------------------------|------------------------------------------------------------------------------------------|
| ENABLEOCRVALIDATIONFOR                 | RUN, DRY                                           | Abilita l'OCR per singolo prodotto                                                       |
| OCRFILTERTEMPORAL                      | CRON_EXPRESSION, DISABLED                          | Abilita l'OCR in modalità RUN per spedizioni in specifiche fasce orarie                  |
| OCRFILTERUNIFIEDDELIVERYDRIVER         | Fulmine, Poste, Sailpost, PostAndService, DISABLED | Abilita l'OCR in modalità RUN per determinati recapitisti                                |
| ENABLEOCRVALIDATIONFORFILE             | PDF                                                | Estensione file abilitata per la validazione OCR                                         |
| SAVEANDNOTSENDTODELIVERYPUSH           | statusCode                                         | StatusCode che non invia a pn-delivery-push ma vengono salvati nel tracking              |
| REQUIREDATTACHMENTSREFINEMENTSTOCK890  | 23L, ARCAD, CAD                                    | Allegati necessari al perfezionamento giacenza 890                                       |
| SENDOCRATTACHMENTSFINALVALIDATION      | AR, Plico, 23L                                     | Allegati da inviare all'OCR per la validazione finale                                    |
| PRODUCTSPROCESSINGMODES                | RUN, DRY                                           | Modalità di processamento per prodotto                                                   |

Per l'elenco completo e i dettagli di tutte le variabili, è possibile consultare il file [application-dev.env](scripts/aws/cfn/application-dev.env).

## Esecuzione
**Prerequisiti**
* Docker/Podman avviato

Eseguire il comando Maven:
```bash
mvn verify
```

