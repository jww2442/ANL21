package geniusweb.exampleparties.learningagent;

import geniusweb.bidspace.BidsWithUtility;
import geniusweb.boa.BoaState;
import geniusweb.boa.acceptancestrategy.AcceptanceStrategy;
import geniusweb.inform.Settings;
import geniusweb.issuevalue.Bid;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.LinearAdditive;

import geniusweb.boa.acceptancestrategy.TimeDependentAcceptanceStrategy;
import geniusweb.profile.utilityspace.UtilitySpace;

import java.util.Random;

public class OurStochasticAcceptanceStrategy extends TimeDependentAcceptanceStrategy {

    private Double min = null, max = null, e = null, k = null;

//    @Override
//    public Boolean isAcceptable(Bid bid, BoaState state) {
//        return null;
//    }

//    protected BidsWithUtility getBidspace(Profile profile) {
//        return new BidsWithUtility((LinearAdditive) profile);
//    }

    //ADDED BELOW METHODS
    /**
     *
     * @param state the { BoaState}
     * @return the parameter e for the time depemdency function
     *         { #f(double)}. The parameter is 1 by default, or the value
     *         for the "e" parameter in the {@link Settings} if available.
     */
    @Override
    protected Double getE(BoaState state) {
        return state.getSettings().getParameters().getDouble("e", 0.1d, 0d, 1d);
    }

    /**
     * Overrideable for hard configuring this component.
     *
     * @param state the {@link BoaState}
     * @return the parameter k for the time depemdency function
     *         { #f(double)}. The parameter is 0 by default, or the value
     *         for the "k" parameter in the {@link Settings} if available.
     */
    @Override
    protected Double getK(BoaState state) {
        return state.getSettings().getParameters().getDouble("k", 0d, 0d, 1d);
    }

    /**
     *
     * @param state the {@link BoaState}
     * @return the min value for { #p(double)}. We use the "min" parameter
     *         in the {@link Settings} if available. If not available, the
     *         parameter is computed as the utility of the reservation bid. If
     *         there is no reservation bid, we use the minimum utility of the
     *         available profile.
     */
    @Override
    protected Double getMin(BoaState state) {
        Double val = state.getSettings().getParameters().getDouble("min", null,
                0d, 1d);
        if (val != null)
            return val;
        // val=null, try the reservation bid
        LinearAdditive profile = (LinearAdditive) state.getProfile();
        if (profile.getReservationBid() != null) {
            return profile.getUtility(profile.getReservationBid())
                    .doubleValue();
        }

        return getBidspace(profile).getRange().getMin().doubleValue();
    }


    /**
     *
     * @param state the {@link BoaState}
     * @return the max value for { #p(double)}. We use the "max" parameter
     *         in the {@link Settings} if available. If not available, we use
     *         the maximum utility of the available profile.
     */
    @Override
    protected Double getMax(BoaState state) {
        Double val = state.getSettings().getParameters().getDouble("max", null,
                0d, 1d);
        if (val != null)
            return val;
        UtilitySpace profile = (UtilitySpace) state.getProfile();
        return getBidspace(profile).getRange().getMax().doubleValue();
    }

    //BEGIN EXPERIMENTAL METHODS
    private double p(double t) {

        double target = this.min + (this.max - this.min) * (1.0 - f(t));
        //truncate below
        if(target<this.min){
            target = this.min;
        } else if(target>this.max){
            target = this.max;
        }
        return target;
    }


    static double start_mean = 0.03;
    static double end_mean = -0.07;
    static double starting_std = 0.1;

    private double f(double t){

        Random r = new Random();

        double s = std(t);
        double m = mean(t);


        double ft = k + (1.0 - k) * Math.pow(t, 1.0 / e);
        double concession = ft + r.nextGaussian()*s + m;

        return concession;
    }

    static double std(double t){

        return starting_std - starting_std*t;
    }

    static double mean(double t){
        double dec = (end_mean - start_mean);
        return dec*t + start_mean;
    }
///////END EXPERIMENTAL METHODS

}
