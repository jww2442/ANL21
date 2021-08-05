package geniusweb.exampleparties.alienmatrixagent;

import geniusweb.bidspace.BidsWithUtility;
import geniusweb.bidspace.Interval;
import geniusweb.bidspace.IssueInfo;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Value;
import geniusweb.profile.utilityspace.LinearAdditive;
import geniusweb.profile.utilityspace.UtilitySpace;
import tudelft.utilities.immutablelist.FixedList;
import tudelft.utilities.immutablelist.ImmutableList;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

public class BidChooser {
    private LinearAdditive utilSpace;
    private BigDecimal toleranceGuess;
    private BidsWithUtility bidutils;
    private BigDecimal minUtil;
    private BigDecimal maxUtil;
    private Map<Bid, BigDecimal> bidCounts;
    private Random random;

    public BidChooser(LinearAdditive space, long randomSeed) {
        this.utilSpace = space;
        bidutils = new BidsWithUtility(utilSpace);
        bidCounts = new HashMap<>();
        computeMinMax();
        computeToleranceGuess();
        random = new Random(randomSeed);
    }

    public BidChooser(LinearAdditive space) {
        this.utilSpace = space;
        bidutils = new BidsWithUtility(utilSpace);
        bidCounts = new HashMap<>();
        computeMinMax();
        computeToleranceGuess();
        random = new Random();
    }

    private void computeMinMax() {
        Interval range = bidutils.getRange();
        this.minUtil = range.getMin();
        this.maxUtil = range.getMax();

        Bid rvbid = utilSpace.getReservationBid();
        if(rvbid != null) {
            BigDecimal rv = utilSpace.getUtility(rvbid);
            if(rv.compareTo(minUtil)>0) {
                minUtil = rv;
            }
        }
    }

    private void computeToleranceGuess() {
        BigDecimal tolerance = BigDecimal.ONE;
        for(IssueInfo iss : bidutils.getInfo()) {
            if(iss.getValues().size().compareTo(BigInteger.ONE) > 0) {
                // we have at least 2 values.
                LinkedList<BigDecimal> values = new LinkedList<BigDecimal>();
                for (Value val : iss.getValues()) {
                    values.add(iss.getWeightedUtil(val));
                }
                Collections.sort(values);
                Collections.reverse(values);
                //CORRECTION
                BigDecimal issueTolerance = BigDecimal.ZERO;
                for (int i = 1; i < values.size(); i++) {
                    issueTolerance = issueTolerance.max(values.get(0).subtract(values.get(1)));
                }
                tolerance = tolerance.min(issueTolerance);
            }
        }
        this.toleranceGuess = tolerance;
    }

    public BigDecimal getMin() {
        return minUtil;
    }

    public BigDecimal getMax() {
        return maxUtil;
    }

    public void withBid(Bid b) {
        withWeightedBid(b, BigDecimal.ONE);
    }

    public void withWeightedBid(Bid b, BigDecimal weight) {
        if(bidCounts.containsKey(b)) {
            bidCounts.put(b, bidCounts.get(b).add(weight));
        }
        else {
            bidCounts.put(b, weight);
        }
    }

    public BigDecimal countBid(Bid b) {
        if(bidCounts.containsKey(b)) {
            return bidCounts.get(b);
        }
        else {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Minimum range of bids is toleranceGuess
     *
     * @param minUtility
     * @param maxUtility
     * @return List of bids within [minUtility, maz(maxUtility, minUtility+toleranceGuess]
     */
    public ImmutableList<Bid> getBids(BigDecimal minUtility, BigDecimal maxUtility) {
        BigDecimal min = minUtility.min(BigDecimal.ONE);
        BigDecimal max = maxUtility.max(minUtility.add(toleranceGuess)).min(BigDecimal.ONE);
        return bidutils.getBids(new Interval(min, max));
    }


    /**
     * Minimum range of bids is toleranceGuess
     *
     * @param utilityGoal
     * @return List of bids within [utilityGoal, utilityGoal+toleranceGuess]
     */
    public ImmutableList<Bid> getBids(BigDecimal utilityGoal) {
        return getBids(utilityGoal, utilityGoal);
    }

    /**
     * Choose a bid using multiple factors
     *
     * @param minUtility
     * @param maxUtility
     * @param goalUtility
     * @param goalWeight
     * @param selfWeight
     * @param oppWeight
     * @param oppModel
     * @param exploreWeight
     * @return The best bid determined by the weights
     */
    public Bid chooseBid(BigDecimal minUtility, BigDecimal maxUtility,
                   BigDecimal goalUtility, BigDecimal goalWeight,
                   BigDecimal selfWeight, BigDecimal oppWeight,
                   UtilitySpace oppModel, BigDecimal exploreWeight,
                   BigDecimal randomWeight) {
        ImmutableList<Bid> bids = getBids(minUtility, maxUtility);
        if(bids.size().compareTo(BigInteger.ZERO) == 0) { //HOPEFULLY THIS SHOULD NOT HAPPEN
            return this.getBids(maxUtil).get(0);
        }

        Bid bestBid = null;
        BigDecimal bestScore = null;
        for(Bid b : bids) {
            BigDecimal bScore = scoreBid(b, goalUtility, goalWeight, selfWeight, oppWeight, oppModel, exploreWeight, randomWeight);
            if(bestScore == null || bScore.compareTo(bestScore) > 0) {
                bestBid = b;
                bestScore = bScore;
            }
        }

        return bestBid;
    }

    private BigDecimal scoreBid(Bid bid, BigDecimal goalUtility, BigDecimal goalWeight,
                BigDecimal selfWeight, BigDecimal oppWeight, UtilitySpace oppModel,
                BigDecimal exploreWeight, BigDecimal randomWeight) {
        BigDecimal score = BigDecimal.ZERO;
        BigDecimal selfUtil = utilSpace.getUtility(bid);
        if(goalWeight.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal goalBase = selfUtil.subtract(goalUtility).abs();
            BigDecimal goalScore = goalBase.multiply(goalWeight.negate());
            score = score.add(goalScore);
        }
        if(selfWeight.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal selfBase = selfUtil;
            BigDecimal selfScore = selfBase.multiply(selfWeight);
            score = score.add(selfScore);
        }
        if(oppModel != null && oppWeight.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal oppBase = oppModel.getUtility(bid);
            BigDecimal oppScore = oppBase.multiply(oppWeight);
            score = score.add(oppScore);
        }
        if(exploreWeight.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal exploreBase = countBid(bid);
            BigDecimal exploreScore = exploreBase.multiply(exploreWeight.negate());
            score = score.add(exploreScore);
        }
        if(randomWeight.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal randomBase = BigDecimal.valueOf(random.nextDouble());
            BigDecimal randomScore = randomBase.multiply(randomWeight);
            score = score.add(randomScore);
        }
        return score;
    }

    public Bid chooseBid(BigDecimal utilityGoal) {
        return chooseBid(utilityGoal, utilityGoal, utilityGoal);
    }

    public Bid chooseBid(BigDecimal minUtility, BigDecimal maxUtility,
                         BigDecimal goalUtility) {
        return chooseBid(minUtility, maxUtility, goalUtility, BigDecimal.ONE, BigDecimal.ZERO);
    }

    public Bid chooseBid(BigDecimal minUtility, BigDecimal maxUtility,
                         BigDecimal goalUtility, BigDecimal goalWeight,
                         BigDecimal selfWeight) {
        return chooseBid(minUtility, maxUtility, goalUtility, goalWeight, selfWeight, BigDecimal.ZERO, null);
    }

    public Bid chooseBid(BigDecimal minUtility, BigDecimal maxUtility,
                         BigDecimal goalUtility, BigDecimal goalWeight,
                         BigDecimal selfWeight, BigDecimal oppWeight,
                         UtilitySpace oppModel) {
        return chooseBid(minUtility, maxUtility, goalUtility, goalWeight, selfWeight, oppWeight, oppModel, BigDecimal.ZERO);
    }

    public Bid chooseBid(BigDecimal minUtility, BigDecimal maxUtility,
                         BigDecimal goalUtility, BigDecimal goalWeight,
                         BigDecimal selfWeight, BigDecimal oppWeight,
                         UtilitySpace oppModel, BigDecimal exploreWeight) {
        return chooseBid(minUtility, maxUtility, goalUtility, goalWeight, selfWeight, oppWeight, oppModel, exploreWeight, BigDecimal.ZERO);
    }
}
