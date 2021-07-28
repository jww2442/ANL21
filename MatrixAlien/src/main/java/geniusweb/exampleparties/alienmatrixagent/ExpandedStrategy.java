package geniusweb.exampleparties.alienmatrixagent;

import geniusweb.actions.*;
import geniusweb.boa.BoaState;
import geniusweb.boa.biddingstrategy.BiddingStrategy;
import geniusweb.boa.biddingstrategy.ExtendedUtilSpace;
import geniusweb.inform.Settings;
import geniusweb.issuevalue.Bid;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.LinearAdditive;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import geniusweb.boa.biddingstrategy.TimeDependentBiddingStrategy;
import geniusweb.profile.utilityspace.UtilitySpace;
import geniusweb.progress.Progress;
import tudelft.utilities.immutablelist.ImmutableList;
import tudelft.utilities.logging.Reporter;

public class ExpandedStrategy {
    // bidSpace=null means we're not yet initialized.
    private BidChooser bidChooser = null;
    private PartyId me;
    private Settings settings;
    private LinearAdditive LAprofile;
    private Profile profile;
    private Reporter reporter;

    private Double e, k, min, max; // min, max attainable utility
    private BigDecimal goalWeight, selfWeight, oppWeight, exploreWeight, randomWeight;


    public ExpandedStrategy(Settings settings, Profile profile, Reporter reporter) {
        init(settings, profile, reporter);
    }

    public void countBid(Bid bid) {
        bidChooser.countBid(bid);
    }

    public Action getAction(Progress progress, UtilitySpace oppModel) {
        BigDecimal utilityGoal = BigDecimal.valueOf(p(progress.get(System.currentTimeMillis())));

        Bid pickedBid = bidChooser.chooseBid(utilityGoal, BigDecimal.valueOf(max), utilityGoal, goalWeight, selfWeight, oppWeight, oppModel, exploreWeight, randomWeight);
        return new Offer(me, pickedBid);
    }

    public Boolean isAcceptable(Bid bid, Progress progress) {
        double targetUtil = p(
                progress.get(System.currentTimeMillis()));
        // we subtract epsilon because of rounding errors in computation.
        return ((UtilitySpace) profile).getUtility(bid)
                .doubleValue() >= targetUtil - 0.0000001;
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

    private void init(Settings settings, Profile profile, Reporter reporter) {
        this.settings = settings;
        this.me = settings.getID();
        this.profile = profile;
        if (!(profile instanceof LinearAdditive))
            throw new IllegalArgumentException(
                    "Requires a LinearAdditive space but got " + profile);
        this.LAprofile = (LinearAdditive) profile;
        this.reporter = reporter;

        this.bidChooser = getBidChooser(this.LAprofile);
        this.e = getE();
        this.k = getK();
        this.min = getMin();
        this.max = getMax();
        this.goalWeight = getGoalWeight();
        this.selfWeight = getSelfWeight();
        this.oppWeight = getOppWeight();
        this.exploreWeight = getExploreWeight();
        this.randomWeight = getRandomWeight();

        if(reporter != null) {
            reporter.log(Level.INFO,
                    "Expanded strategy min util = " + this.min);
        }
    }

    //SETTERS BELOW

    protected BidChooser getBidChooser(LinearAdditive profile) {
        return new BidChooser(profile);
    }

    protected Double getE() {
        return settings.getParameters().getDouble("e", 0.03d, 0d, 1d);
    }

    protected Double getK() {
        return settings.getParameters().getDouble("k", 0d, 0d, 1d);
    }

    protected Double getMin() {
        Double val = settings.getParameters().getDouble("min", null,
                0d, 1d);
        if (val != null)
            return val;
        // val=null, try the reservation bid
        LinearAdditive lAProfile = LAprofile;
        if (lAProfile.getReservationBid() != null) {
            return lAProfile.getUtility(LAprofile.getReservationBid())
                    .doubleValue();
        }

        return bidChooser.getMin().doubleValue();
    }

    protected Double getMax() {
        Double val = settings.getParameters().getDouble("max", null,
                0d, 1d);
        if (val != null)
            return val;
        return bidChooser.getMax().doubleValue();
    }

    protected BigDecimal getGoalWeight() {
        return BigDecimal.valueOf(settings.getParameters().getDouble("gw", 1d, 0d, 100d));
    }

    protected BigDecimal getSelfWeight() {
        return BigDecimal.valueOf(settings.getParameters().getDouble("sw", 0d, 0d, 100d));
    }

    protected BigDecimal getOppWeight() {
        return BigDecimal.valueOf(settings.getParameters().getDouble("ow", 0d, 0d, 100d));
    }

    protected BigDecimal getExploreWeight() {
        return BigDecimal.valueOf(settings.getParameters().getDouble("ew", 0d, 0d, 100d));
    }

    protected BigDecimal getRandomWeight() {
        return BigDecimal.valueOf(settings.getParameters().getDouble("rw", 0d, 0d, 100d));
    }



}