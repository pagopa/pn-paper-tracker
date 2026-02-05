# pn-paper-tracker

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
* RS, RIS (Raccomandata Semplice, Raccomandati Internazionale Semplice) (in roadmap)

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

### Dipendenze interne


### Dipendenze esterne


## API & Documentazione

## Allarmi e monitoraggio

## Configurazione

## Esecuzione
**Prerequisiti**
* Docker/Podman avviato
