package geniusweb.exampleparties.alienmatrixagent;

import geniusweb.actions.*;
import geniusweb.boa.BoaState;
import geniusweb.boa.biddingstrategy.BiddingStrategy;
import geniusweb.boa.biddingstrategy.ExtendedUtilSpace;
import geniusweb.issuevalue.Bid;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.LinearAdditive;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import geniusweb.boa.biddingstrategy.TimeDependentBiddingStrategy;
import tudelft.utilities.immutablelist.ImmutableList;

public class OurStochasticBiddingStrategy extends TimeDependentBiddingStrategy {

    // bidSpace=null means we're not yet initialized.
    private ExtendedUtilSpace bidSpace = null;
    private PartyId me;

    //added bc these are private in superclass
    private Double e, k, min, max; // min, max attainable utility


    //    @Override
//    public Action getAction(BoaState state) {
//        if (bidSpace == null) {
//            init(state);
//        }
//        //TODO Choose Action here
//        return null;
//    }
    @Override
    public Action getAction(BoaState state) {
        if (bidSpace == null) {
            init(state);
        }

        double utilityGoal = p(
                state.getProgress().get(System.currentTimeMillis()));

        // if there is no opponent model available
        ImmutableList<Bid> bidOptions = bidSpace
                .getBids((BigDecimal.valueOf(utilityGoal)));

        if (bidOptions.size().intValue() == 0) {
            // should not happen, emergency exit
            state.getReporter().log(Level.WARNING,
                    "No viable bids found around current utility target");
            Bid lastBid = getLastBid(state.getActionHistory());
            if (lastBid == null)
                return new EndNegotiation(me);
            return new Accept(me, lastBid);
        }
        Bid pickedBid = bidOptions.get(ThreadLocalRandom.current()
                .nextInt(bidOptions.size().intValue()));
        return new Offer(me, pickedBid);

    }


    ///////BEGIN EXPERIMENTAL METHODS

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

    private Bid getLastBid(List<Action> history) {
        for (int n = history.size() - 1; n >= 0; n--) {
            Action action = history.get(n);
            if (action instanceof Offer) {
                return ((Offer) action).getBid();
            }
        }
        return null;
    }


//    private void init(BoaState state) {
//        this.me = state.getSettings().getID();
//        Profile prof = state.getProfile();
//        if (!(prof instanceof LinearAdditive))
//            throw new IllegalArgumentException(
//                    "Requires a LinearAdditive space but got " + prof);
//        LinearAdditive profile = (LinearAdditive) prof;
//
//        this.bidSpace = getBidSpace(profile);
//        //TODO: Assign private vars here
//
//    }

    private void init(BoaState state) {
        this.me = state.getSettings().getID();
        Profile prof = state.getProfile();
        if (!(prof instanceof LinearAdditive))
            throw new IllegalArgumentException(
                    "Requires a LinearAdditive space but got " + prof);
        LinearAdditive profile = (LinearAdditive) prof;

        this.bidSpace = getBidSpace(profile);
        this.e = getE(state);
        this.k = getK(state);
        this.min = getMin(state);
        this.max = getMax(state);

        state.getReporter().log(Level.INFO,
                "BOA biddingstrategy min util = " + this.min);
    }

//    @Override
//    protected ExtendedUtilSpace getBidSpace(LinearAdditive profile) {
//        return new ExtendedUtilSpace(profile);
//    }

    //SETTERS BELOW
    @Override
    protected Double getE(BoaState state) {
        return state.getSettings().getParameters().getDouble("e", 0.1d, 0d, 1d);
    }

    @Override
    protected Double getK(BoaState state) {
        return state.getSettings().getParameters().getDouble("k", 0d, 0d, 1d);
    }

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

        return bidSpace.getMin().doubleValue();
    }

    @Override
    protected Double getMax(BoaState state) {
        Double val = state.getSettings().getParameters().getDouble("max", null,
                0d, 1d);
        if (val != null)
            return val;
        return bidSpace.getMax().doubleValue();
    }

}
