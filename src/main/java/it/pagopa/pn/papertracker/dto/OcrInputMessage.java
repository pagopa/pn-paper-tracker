package it.pagopa.pn.papertracker.dto;

import lombok.Data;

@Data
public class OcrInputMessage {
    private String version;
    private String CommandId; // identificativo della richiesta
    private String commandType;
    private DataField data;

    @Data
    public static class DataField {
        private ProductType productType; // AR
        private DocumentType documentType; // AR, PLICO
        private UnifiedDeliveryDriver unifiedDeliveryDriver; // Poste, Sailpost, PostAndService, Fulmine
        private Details details;
    }

    @Data
    public static class Details {
        private String deliveryDetailCode; // statusCode
        private String notificationDate;
        private String registeredLetterCode;
        private String deliveryFailureCause; // required se deliveryDetailCode == RECRN002B o RECRN002E
        private String attachment; // attachment.uri
    }

    public enum ProductType {
        AR
    }

    public enum DocumentType {
        PLICO
    }

    public enum UnifiedDeliveryDriver {
        Poste, Sailpost, PostAndService, Fulmine
    }
}