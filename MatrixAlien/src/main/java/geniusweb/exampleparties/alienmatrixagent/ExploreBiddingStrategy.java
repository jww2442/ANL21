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
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import geniusweb.boa.biddingstrategy.TimeDependentBiddingStrategy;
import tudelft.utilities.immutablelist.ImmutableList;

public class ExploreBiddingStrategy implements BiddingStrategy {
    // bidSpace=null means we're not yet initialized.
    private ExtendedUtilSpace bidSpace = null;
    private PartyId me;

    private Double e, k, min, max; // min, max attainable utility

    private Double exp, opp; // how much to rely upon opponent model and exploration
    private Map<Bid, Integer> bidFrequencies; // How often each bid has been offered

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

    private double p(double t) {
        return this.min + (this.max - this.min) * (1.0 - f(t));
    }

    private double f(double t) {
        double ft = k + (1.0 - k) * Math.pow(t, 1.0 / e);
        return ft;
    }

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
    protected Double getE(BoaState state) {
        return state.getSettings().getParameters().getDouble("e", 0.03d, 0d, 1d);
    }

    protected Double getK(BoaState state) {
        return state.getSettings().getParameters().getDouble("k", 0d, 0d, 1d);
    }

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

    protected Double getMax(BoaState state) {
        Double val = state.getSettings().getParameters().getDouble("max", null,
                0d, 1d);
        if (val != null)
            return val;
        return bidSpace.getMax().doubleValue();
    }

    protected ExtendedUtilSpace getBidSpace(LinearAdditive profile) {
        return new ExtendedUtilSpace(profile);
    }

}