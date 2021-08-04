package geniusweb.exampleparties.alienmatrixagent; // TODO: change name

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

/**
 * The class hold the negotiation data that is obtain during a negotiation
 * session. It will be saved to disk after the negotiation has finished. During
 * the learning phase, this negotiation data can be used to update the
 * persistent state of the agent. NOTE that Jackson can serialize many default
 * java classes, but not custom classes out-of-the-box.
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public class NegotiationData {

    private Double maxReceivedUtil = 0.0;
    private Double agreementUtil = 0.0;
    private String opponentName;

    private Double eVal = 2.0e-8;
    private Double timeTakenToAgree = -1.0;
    private int numEncounters = 0;

    public void multeVal(){
        if(this.agreementUtil >90.0) {
            ;
        }else if(this.agreementUtil > 0.0 && this.agreementUtil <= 90.0) {
            double timeLeft = 60 * (1.0 - timeTakenToAgree);
            for (int i = 0; i < timeLeft; i++) {
                this.eVal *= 0.95;
            }
            for(int i = 0; i < numEncounters; i++){
                this.eVal *= 1.005;
            }
        } else if(this.agreementUtil == 0.0){
            this.eVal *= 10;
            for(int i = 0; i < numEncounters; i++){
                this.eVal *= 1.005;
            }
        }

    }

    public void setNumEncounters(int num){
        this.numEncounters = num;
    }

    public void setTimeTaken(Double timeTaken){
        this.timeTakenToAgree = timeTaken;
    }

    public void seteVal(Double eval){
        this.eVal = eval;
    }

    public void addAgreementUtil(Double agreementUtil) {
        this.agreementUtil = agreementUtil;
        if (agreementUtil > maxReceivedUtil)
            this.maxReceivedUtil = agreementUtil;
    }

    public void addBidUtil(Double bidUtil) {
        if (bidUtil > maxReceivedUtil)
            this.maxReceivedUtil = bidUtil;
    }

    public void setOpponentName(String opponentName) {
        this.opponentName = opponentName;
    }

    public String getOpponentName() {
        return this.opponentName;
    }

    public Double geteVal(){
        return this.eVal;
    }

    public Double getMaxReceivedUtil() {
        return this.maxReceivedUtil;
    }

    public Double getAgreementUtil() {
        return this.agreementUtil;
    }
}
