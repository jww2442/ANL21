package geniusweb.exampleparties.alienmatrixagent;

import geniusweb.actions.Action;
import geniusweb.actions.Offer;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.opponentmodel.OpponentModel;
import geniusweb.profile.utilityspace.UtilitySpace;
import geniusweb.progress.Progress;

import java.math.BigDecimal;
import java.util.*;

import org.apache.commons.math3.stat.inference.ChiSquareTest;


public class TimeIndependentFreqModel implements UtilitySpace, OpponentModel {
    private static int serial = 1; // counter for auto name generation

    private final Domain domain;
    private final Bid resBid;

    private final double gamma;
    private final double alpha;
    private final double beta;
    private final int timeBracketCount;

    private final double issueWeightTotal;
    private final BigDecimal currentTimeBracket;
    private final BigDecimal previousTimeBracket;
    private final Map<String, Double> issueWeights;
    private final Map<String, Map<Value, Double>> valueWeights;
    private final Map<Double, TimePeriod> timeBidFrequencies;

    public TimeIndependentFreqModel() {
        this.domain = null;
        this.resBid = null;
        this.issueWeightTotal = 0.0;
        this.currentTimeBracket = null;
        this.previousTimeBracket = null;
        this.issueWeights = null;
        this.valueWeights = null;
        this.timeBidFrequencies = null;

        this.gamma = 0.25;
        this.alpha = 10;
        this.beta = 5;
        this.timeBracketCount = 100;
    }

    protected TimeIndependentFreqModel(Domain domain, Bid resBid,
                                       double gamma, double alpha, double beta, int timeBracketCount,
                                       double issueWeightTotal, BigDecimal currentTimeBracket,
                                       BigDecimal previousTimeBracket, Map<String, Double> issueWeights,
                                       Map<String, Map<Value, Double>> valueWeights,
                                       Map<Double, TimePeriod> timeBidFrequencies) {
        if(domain == null) {
            throw new IllegalStateException("domain is not initialized");
        }
        this.domain = domain;
        this.resBid = resBid;
        this.gamma = gamma;
        this.alpha = alpha;
        this.beta = beta;
        this.timeBracketCount = timeBracketCount;
        this.issueWeightTotal = issueWeightTotal;
        this.currentTimeBracket = currentTimeBracket;
        this.previousTimeBracket = previousTimeBracket;
        this.issueWeights = issueWeights;
        this.valueWeights = valueWeights;
        this.timeBidFrequencies = timeBidFrequencies;
    }

    protected static class TimePeriod {
        public final Map<String, Map<Value, Integer>> bidFrequencies;
        public final int totalBids;
        public final BigDecimal endTime;

        public TimePeriod(BigDecimal endTime, Map<String, Map<Value, Integer>> bidFrequencies,
                          int totalBids) {
            this.bidFrequencies = bidFrequencies;
            this.totalBids = totalBids;
            this.endTime = endTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TimePeriod)) return false;
            TimePeriod that = (TimePeriod) o;
            return totalBids == that.totalBids && Objects.equals(bidFrequencies, that.bidFrequencies) && Objects.equals(endTime, that.endTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bidFrequencies, totalBids, endTime);
        }

        @Override
        public String toString() {
            return "TimePeriod{" +
                    "bidFrequencies=" + bidFrequencies +
                    ", totalBids=" + totalBids +
                    ", endTime=" + endTime +
                    '}';
        }
    }

    private BigDecimal getCurrentTimeBracket(Progress progress) {
        double currentProgress = progress.get(System.currentTimeMillis());
        int startTimeBracket = (int) Math.floor(currentProgress * timeBracketCount);
        return BigDecimal.valueOf((startTimeBracket + 1) / (double) timeBracketCount);
    }

    private double omega(double t) {
        return 1 - Math.pow(t, 1.0 / 3.0);
    }

    private double delta(double t) {
        return alpha * (1 - Math.pow(t, beta));
    }

    private double pRatio(String issue, Value value, TimePeriod timePeriod) {
        if(timePeriod.bidFrequencies.get(issue).containsKey(value)) {
            return 0.0;
        }
        return timePeriod.bidFrequencies.get(issue).get(value).doubleValue() / (double) timePeriod.totalBids;
    }

    private double Freq(String issue, Value value, TimePeriod timePeriod) {
        int numerator = 1 + timePeriod.bidFrequencies.get(issue).get(value);
        int denominator = domain.getValues(issue).size().intValue() + timePeriod.totalBids;
        return (double) numerator / (double) denominator;
    }

    private Map<String, Map<Value, Double>> updatedValueWeights() {
        HashMap<String, Map<Value, Double>> newValueWeights = new HashMap<>();

        for (String issue : domain.getIssues()) {
            Map<Value, Double> fractionSums = new HashMap<>();
            Map<Value, Double> issueValues = new HashMap<>();
            double denominator = 1.0;
            for (Value value : domain.getValues(issue)) {
                double fractionSum = 1.0;
                for (TimePeriod timePeriod : timeBidFrequencies.values()) {
                    fractionSum += omega(timePeriod.endTime.doubleValue()) * pRatio(issue, value, timePeriod);
                }
                fractionSum = Math.pow(fractionSum, gamma);
                denominator = Math.max(denominator, fractionSum);
                fractionSums.put(value, fractionSum);
            }
            for (Value value : domain.getValues(issue)) {
                double valueWeight = fractionSums.get(value) / denominator;
                issueValues.put(value, valueWeight);
            }
            newValueWeights.put(issue, issueValues);
        }
        return newValueWeights;
    }

    private Map<String, Double> updatedIssueWeights(Map<String, Map<Value, Double>> newValueWeights) {
        TimePeriod previousPeriod = timeBidFrequencies.get(previousTimeBracket.doubleValue());
        TimePeriod currentPeriod = timeBidFrequencies.get(currentTimeBracket.doubleValue());
        Set<String> issues = domain.getIssues();
        HashMap<String, Boolean> issuesUnchanged = new HashMap<>();
        HashMap<String, Double> newIssueWeights = new HashMap<>();
        boolean concession = false;

        for (String issue : issues) {
            ValueSet values = domain.getValues(issue);
            Map<Value, Double> issueVals = newValueWeights.get(issue);
            int prevBidCount = previousPeriod.totalBids;
            int currBidCount = currentPeriod.totalBids;
            HashMap<Value, Double> oldFreqMap = new HashMap<>();
            HashMap<Value, Double> newFreqMap = new HashMap<>();
            long[] oldFreqVect = new long[values.size().intValue()];
            long[] newFreqVect = new long[values.size().intValue()];

            int i = 0;
            for (Value value : values) {
                double oldFreq = Freq(issue, value, previousPeriod);
                double newFreq = Freq(issue, value, currentPeriod);
                oldFreqMap.put(value, oldFreq);
                newFreqMap.put(value, newFreq);
                // Trying using just the numerator TODO: This is a change from the document
                oldFreqVect[i] = 1 + previousPeriod.bidFrequencies.get(issue).get(value);
                newFreqVect[i] = 1 + currentPeriod.bidFrequencies.get(issue).get(value);
            }

            ChiSquareTest chiSquareTest = new ChiSquareTest();
            double pVal = chiSquareTest.chiSquareDataSetsComparison(oldFreqVect, newFreqVect);
            if (pVal > 0.05) {
                issuesUnchanged.put(issue, true);
            } else {
                double averageUtilPrev = 0.0;
                double averageUtilCurr = 0.0;
                for (Value value : values) {
                    averageUtilPrev += issueVals.get(value) * oldFreqMap.get(value) / prevBidCount;
                    averageUtilCurr += issueVals.get(value) * newFreqMap.get(value) / currBidCount;
                }
                if (averageUtilCurr < averageUtilPrev) {
                    concession = true;
                }
            }
        }

        if (concession) {
            for (String issue : domain.getIssues()) {
                if(!issuesUnchanged.get(issue)) {
                    double newWeight = issueWeights.get(issue) + delta(currentTimeBracket.doubleValue());
                    newIssueWeights.put(issue, newWeight);
                }
            }
        }

        return newIssueWeights;
    }

    @Override
    public TimeIndependentFreqModel with(Domain domain, Bid resBid) {
        if(domain == null) {
            throw new NullPointerException("domain is not initialized");
        }

        Map<String, Double> newIssueWeights = new HashMap<>();
        Map<String, Map<Value, Double>> newValueWeights = new HashMap<>();
        Map<Double, TimePeriod> newTimeBidFrequencies = new HashMap<>();
        double newIssueWeightTotal = 0.0;
        for(String issue: domain.getIssues()) {
            double defaultWeight = 1.0;
            newIssueWeights.put(issue, defaultWeight);
            newIssueWeightTotal += defaultWeight;
            Map<Value, Double> issueValues = new HashMap<>();
            for(Value value: domain.getValues(issue)) {
                issueValues.put(value, 1.0);
            }
            newValueWeights.put(issue, issueValues);
        }

        return new TimeIndependentFreqModel(domain, resBid, gamma, alpha, beta,
                timeBracketCount, newIssueWeightTotal, null, null,
                newIssueWeights, newValueWeights, newTimeBidFrequencies);
    }

    @Override
    public BigDecimal getUtility(Bid bid) {
        checkDomainNull();
        double util = 0.0;
        for(String issue: bid.getIssues()) {
            double valueUtil = valueWeights.get(issue).get(bid.getValue(issue));
            util += issueWeights.get(issue) / issueWeightTotal * valueUtil;
        }
        //Presumably 0.0 <= util <= 1.0
        return new BigDecimal(util);
    }

    @Override
    public String getName() {
        checkDomainNull();
        return "TimeIndependentFreqModel" + (serial++) + "For" + domain;
    }

    @Override
    public Domain getDomain() {
        return domain;
    }

    @Override
    public TimeIndependentFreqModel with(Action action, Progress progress) {
        checkDomainNull();
        if(!(action instanceof Offer)) {
            return this;
        }
        Bid bid = ((Offer) action).getBid();
        BigDecimal timeBracket = getCurrentTimeBracket(progress);

        Map<String, Map<Value, Double>> newValueWeights = valueWeights;
        Map<String, Double> newIssueWeights = issueWeights;
        double newIssueWeightTotal = issueWeightTotal;
        BigDecimal newPreviousTimeBracket = previousTimeBracket;
        if(!timeBracket.equals(currentTimeBracket)) {
            newPreviousTimeBracket = currentTimeBracket;
            if(currentTimeBracket == null) {
                newValueWeights = valueWeights;
                newIssueWeights = issueWeights;
            }
            else if(previousTimeBracket == null) {
                newValueWeights = updatedValueWeights();
                newIssueWeights = issueWeights;
            } else {
                newValueWeights = updatedValueWeights();
                newIssueWeights = updatedIssueWeights(newValueWeights);
            }
            newIssueWeightTotal = 0.0;
            for(String issue: domain.getIssues()) {
                newIssueWeightTotal += newIssueWeights.get(issue);
            }
        }

        Map<Double, TimePeriod> newTimeBidFrequencies = new HashMap<>();
        for(double time: timeBidFrequencies.keySet()) {
            if(time != timeBracket.doubleValue()) {
                newTimeBidFrequencies.put(time, timeBidFrequencies.get(time));
            }
        }

        newTimeBidFrequencies.put(timeBracket.doubleValue(), addBid(bid, timeBracket));

        return new TimeIndependentFreqModel(domain, resBid, gamma, alpha, beta, timeBracketCount,
                newIssueWeightTotal, timeBracket, newPreviousTimeBracket, newIssueWeights,
                newValueWeights, newTimeBidFrequencies);
    }

    private TimePeriod addBid(Bid bid, BigDecimal timeBracket) {
        double time = timeBracket.doubleValue();
        int newTotalBids = 0;

        Map<String, Map<Value, Integer>> newBidFrequencies = null;
        if(timeBidFrequencies.containsKey(time)) {
            newBidFrequencies = cloneMap(timeBidFrequencies.get(time).bidFrequencies);
            newTotalBids = timeBidFrequencies.get(time).totalBids + 1;
        }
        else {
            newBidFrequencies = new HashMap<>();
            for(String issue: domain.getIssues()) {
                Map<Value, Integer> issueValues = new HashMap<>();
                for(Value value: domain.getValues(issue)) {
                    issueValues.put(value, 0);
                }
                newBidFrequencies.put(issue, issueValues);
            }
            newTotalBids = 1;
        }

        for (String issue : domain.getIssues()) {
            Map<Value, Integer> freqs = newBidFrequencies.get(issue);
            Value value = bid.getValue(issue);
            if(value != null) {
                Integer oldfreq = freqs.get(value);
                if(oldfreq == null) {
                    oldfreq = 0;
                }
                freqs.put(value, oldfreq + 1);
            }
        }

        return new TimePeriod(currentTimeBracket, newBidFrequencies, newTotalBids);
    }

    private static Map<String, Map<Value, Integer>> cloneMap(
            Map<String, Map<Value, Integer>> freqs) {
        Map<String, Map<Value, Integer>> map = new HashMap<>();
        for (String issue : freqs.keySet()) {
            map.put(issue, new HashMap<Value, Integer>(freqs.get(issue)));
        }
        return map;
    }

    @Override
    public Bid getReservationBid() {
        return resBid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getDomain(), resBid, gamma, alpha, beta, timeBracketCount, issueWeightTotal, currentTimeBracket, previousTimeBracket, issueWeights, valueWeights, timeBidFrequencies);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeIndependentFreqModel)) return false;
        TimeIndependentFreqModel that = (TimeIndependentFreqModel) o;
        return Double.compare(that.gamma, gamma) == 0 && Double.compare(that.alpha, alpha) == 0 && Double.compare(that.beta, beta) == 0 && timeBracketCount == that.timeBracketCount && Double.compare(that.issueWeightTotal, issueWeightTotal) == 0 && Objects.equals(getDomain(), that.getDomain()) && Objects.equals(resBid, that.resBid) && Objects.equals(currentTimeBracket, that.currentTimeBracket) && Objects.equals(previousTimeBracket, that.previousTimeBracket) && Objects.equals(issueWeights, that.issueWeights) && Objects.equals(valueWeights, that.valueWeights) && Objects.equals(timeBidFrequencies, that.timeBidFrequencies);
    }

    @Override
    public String toString() {
        return "LearningAgentOpponentModel[" + timeBidFrequencies + "]";
    }

    private void checkDomainNull() {
        if (domain == null) {
            throw new IllegalStateException("domain is not initialized");
        }
    }
}
