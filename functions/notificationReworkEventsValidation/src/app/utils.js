function checkExpected(reworkEntry, statusCode, attachments, deliveryFailureCause){
   if (!reworkEntry?.expectedStatusCodes || !Array.isArray(reworkEntry.expectedStatusCodes)) {
        return false;
   }
   return reworkEntry.expectedStatusCodes.some(item => {
     if (item.statusCode !== statusCode) return false;

     const hasInputAttachments = attachments && attachments.length > 0;
     const hasItemAttachments = item.attachments && item.attachments.length > 0;

     if (hasInputAttachments && !hasItemAttachments) return false;

     // Caso: item ha allegati → controlla corrispondenza parziale (input ⊆ item)
     if (hasItemAttachments) {
       const isSubset = attachments.every(att => item.attachments.includes(att));
       const sameLength = item.attachments.length === attachments.length;
       // true solo se uguali o se input ⊂ item (come [a] vs [a,b])
       return isSubset && (sameLength || item.attachments.length > attachments.length);
     }

     if(deliveryFailureCause){
        return reworkEntry.deliveryFailureCause && reworkEntry.deliveryFailureCause === deliveryFailureCause;
     }

     return false;
   });
}

function checkAlreadyReceived(reworkEntry, statusCode, attachments, statusDateTime){
  if (!reworkEntry?.receivedStatusCodes || !Array.isArray(reworkEntry.receivedStatusCodes)) {
    return false;
  }
  return reworkEntry.receivedStatusCodes.some(item => {
    if (item.statusCode !== statusCode || item.statusDateTime !== statusDateTime) {
      return false;
    }
    
    const hasInputAttachments = attachments && attachments.length > 0;
    const hasItemAttachments = item.attachments && item.attachments.length > 0;
    
    if (hasInputAttachments !== hasItemAttachments) {
      return false;
    }
    
    if (hasInputAttachments && hasItemAttachments) {
      return item.attachments.length === attachments.length && 
           item.attachments.every(att => attachments.includes(att));
    }
    return true;
  });
}

function checkRejectedStatusCode(statusCode){
    const INVALID_STATUS_CODE = process.env.INVALID_STATUS_CODE ? process.env.INVALID_STATUS_CODE.split(",") : [];
    if (INVALID_STATUS_CODE.includes(statusCode)) {
        return true;
    }
    return false;
}

function buildReceivedStatusCodeEntry(statusCode, attachments, statusDateTime){
    return {
        statusCode,
        attachments: attachments && attachments.length > 0 ? attachments : [],
        statusDateTime
    }
}

module.exports = {
    checkExpected,
    checkAlreadyReceived,
    checkRejectedStatusCode,
    buildReceivedStatusCodeEntry
}