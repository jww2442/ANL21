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
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import geniusweb.boa.biddingstrategy.TimeDependentBiddingStrategy;
import tudelft.utilities.immutablelist.ImmutableList;

public class ExpandedBiddingStrategy implements BiddingStrategy {
    // bidSpace=null means we're not yet initialized.
    private BidChooser bidChooser = null;
    private PartyId me;

    private Double e, k, min, max; // min, max attainable utility
    private BigDecimal goalWeight, selfWeight, oppWeight, exploreWeight, randomWeight;

    @Override
    public Action getAction(BoaState state) {
        if (bidChooser == null) {
            init(state);
        }
        BigDecimal utilityGoal = BigDecimal.valueOf(p(state.getProgress().get(System.currentTimeMillis())));

        Bid pickedBid = bidChooser.chooseBid(utilityGoal, BigDecimal.valueOf(max), utilityGoal, goalWeight, selfWeight, oppWeight, null, exploreWeight, randomWeight);
        //Could Not Get Hold of Opponent Model as UtilitySpace
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

    private void init(BoaState state) {
        this.me = state.getSettings().getID();
        Profile prof = state.getProfile();
        if (!(prof instanceof LinearAdditive))
            throw new IllegalArgumentException(
                    "Requires a LinearAdditive space but got " + prof);
        LinearAdditive profile = (LinearAdditive) prof;

        this.bidChooser = getBidChooser(profile);
        this.e = getE(state);
        this.k = getK(state);
        this.min = getMin(state);
        this.max = getMax(state);
        this.goalWeight = getGoalWeight(state);
        this.selfWeight = getSelfWeight(state);
        this.oppWeight = getOppWeight(state);
        this.exploreWeight = getExploreWeight(state);
        this.randomWeight = getRandomWeight(state);

        state.getReporter().log(Level.INFO,
                "Expanded biddingstrategy min util = " + this.min);
    }

    //SETTERS BELOW

    protected BidChooser getBidChooser(LinearAdditive profile) {
        return new BidChooser(profile);
    }

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

        return bidChooser.getMin().doubleValue();
    }

    protected Double getMax(BoaState state) {
        Double val = state.getSettings().getParameters().getDouble("max", null,
                0d, 1d);
        if (val != null)
            return val;
        return bidChooser.getMax().doubleValue();
    }

    protected BigDecimal getGoalWeight(BoaState state) {
        return BigDecimal.valueOf(state.getSettings().getParameters().getDouble("gw", 1d, 0d, 100d));
    }

    protected BigDecimal getSelfWeight(BoaState state) {
        return BigDecimal.valueOf(state.getSettings().getParameters().getDouble("sw", 0d, 0d, 100d));
    }

    protected BigDecimal getOppWeight(BoaState state) {
        return BigDecimal.valueOf(state.getSettings().getParameters().getDouble("ow", 0d, 0d, 100d));
    }

    protected BigDecimal getExploreWeight(BoaState state) {
        return BigDecimal.valueOf(state.getSettings().getParameters().getDouble("ew", 0d, 0d, 100d));
    }

    protected BigDecimal getRandomWeight(BoaState state) {
        return BigDecimal.valueOf(state.getSettings().getParameters().getDouble("rw", 0d, 0d, 100d));
    }



}