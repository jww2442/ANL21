package geniusweb.exampleparties.alienmatrixagent;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

import java.awt.geom.Arc2D;

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
    private Double timeTakenToAgree = 0.0;
    private int numEncounters = 0;
    private Double minVal = 0.23;

    private static final double GOOD_AGREEMENT_UTIL = 0.850;
    private static final double NO_AGREEMENT_UTIL = 0.001;
    private static final double UTIL_NEAR_MIN_DISTANCE = 0.05;
    private static final int EARLY_ROUND_E_INCREASE_COUNT = 5;

    private static final double INCREASE_E_FACTOR = 10.0;
    private static final double TINY_POINTLESS_JUST_TO_PROVE_A_POINT_ELECTRIC_BOOGALOO_LONGEST_CONSTANT_YOUVE_EVER_SEEN_INCREASE_E_FACTOR = 3.141592653589793238368343;
    private static final double DECREASE_E_FACTOR = 0.631;
    private static final double INCREASE_MIN_DELTA = 0.025;
    private static final double DECREASE_MIN_DELTA = -0.05;

    private static final double E_MINIMUM = 0.000000000001;
    private static final double E_MAXIMUM = 0.1;
    private static final double MIN_MINIMUM = 0.4;
    private static final double MIN_MAXIMUM = 0.7;

    public void changeEandMin(){
        if(this.agreementUtil > GOOD_AGREEMENT_UTIL) {
            ;
        }else if(this.agreementUtil > NO_AGREEMENT_UTIL && this.agreementUtil <= GOOD_AGREEMENT_UTIL) {
            this.eVal *= DECREASE_E_FACTOR;

            if((this.agreementUtil - this.minVal) < UTIL_NEAR_MIN_DISTANCE) {
                this.minVal += INCREASE_MIN_DELTA;
            }

        } else if(this.agreementUtil <= NO_AGREEMENT_UTIL){
            this.eVal *= INCREASE_E_FACTOR;

            this.minVal += DECREASE_MIN_DELTA;
        }

        if(numEncounters == EARLY_ROUND_E_INCREASE_COUNT) {
            this.eVal *= TINY_POINTLESS_JUST_TO_PROVE_A_POINT_ELECTRIC_BOOGALOO_LONGEST_CONSTANT_YOUVE_EVER_SEEN_INCREASE_E_FACTOR;
        }

        if (this.minVal < MIN_MINIMUM){
            this.minVal = MIN_MINIMUM;
        }
        if(this.minVal > MIN_MAXIMUM){
            this.minVal = MIN_MAXIMUM;
        }
        if(this.eVal > E_MAXIMUM){
            this.eVal = E_MAXIMUM;
        }
        if(this.eVal < E_MINIMUM){
            this.eVal = E_MAXIMUM;
        }

    }



    public Double getMinVal(){
        return this.minVal;
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

    public void setMinVal(Double minval){
        this.minVal = minval;
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
