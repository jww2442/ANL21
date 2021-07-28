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
import tudelft.utilities.immutablelist.Tuple;

import javax.swing.*;

import javax.swing.JFrame;

public class SmallTimeIndependentFreqModel implements UtilitySpace, OpponentModel {
    private static int serial = 1; // counter for auto name generation

    private final Domain domain;
    private final Bid resBid;

    private final double gamma;
    private final double alpha;
    private final double beta;
    private final int timeBracketCount;

    private final double issueWeightTotal;
    private final Double currentTimeBracket;
    private final Double previousTimeBracket;
    private final Map<String, Map<Value, Double>> evalSums;
    private final Map<String, Map<Value, Integer>> currentFrequencies;
    private final Integer currentBidCount;
    private final Map<String, Map<Value, Integer>> previousFrequencies;
    private final Integer previousBidCount;
    private final Map<String, Double> issueWeights;
    private final Map<String, Map<Value, Double>> valueWeights;

    public SmallTimeIndependentFreqModel() {
        this.domain = null;
        this.resBid = null;
        this.issueWeightTotal = 0.0;
        this.currentTimeBracket = null;
        this.previousTimeBracket = null;
        this.currentFrequencies = null;
        this.currentBidCount = null;
        this.previousFrequencies = null;
        this.previousBidCount = null;
        this.evalSums = null;
        this.issueWeights = null;
        this.valueWeights = null;

        this.gamma = 0.25;
        this.alpha = 10;
        this.beta = 5;
        this.timeBracketCount = 100;
    }

    protected SmallTimeIndependentFreqModel(Domain domain, Bid resBid,
                                            double gamma, double alpha, double beta, int timeBracketCount,
                                            double issueWeightTotal,
                                            Double currentTimeBracket, Double previousTimeBracket,
                                            Map<String, Map<Value, Integer>> currentFrequencies, Integer currentBidCount,
                                            Map<String, Map<Value, Integer>> previousFrequencies, Integer previousBidCount,
                                            Map<String, Map<Value, Double>> evalSums,
                                            Map<String, Double> issueWeights,
                                            Map<String, Map<Value, Double>> valueWeights) {
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
        this.previousFrequencies = previousFrequencies;
        this.previousBidCount = previousBidCount;
        this.currentFrequencies = currentFrequencies;
        this.currentBidCount = currentBidCount;
        this.evalSums = evalSums;
        this.issueWeights = issueWeights;
        this.valueWeights = valueWeights;
    }

    private Double getCurrentTimeBracket(Progress progress) {
        double currentProgress = progress.get(System.currentTimeMillis());
        int startTimeBracket = (int) Math.floor(currentProgress * timeBracketCount);
        return (startTimeBracket + 1) / (double) timeBracketCount;
    }

    private double omega(double t) {
        return 1 - Math.pow(t, 1.0 / 3.0);
    }

    private double delta(double t) {
        return alpha * (1 - Math.pow(t, beta));
    }

    private double pRatio(String issue, Value value, Map<String, Map<Value, Integer>> bidFrequencies, Integer totalBids) {
        if(!bidFrequencies.get(issue).containsKey(value)) {
            return 0.0;
        }
        return bidFrequencies.get(issue).get(value).doubleValue() / (double) totalBids;
    }

    private double Freq(String issue, Value value, Map<String, Map<Value, Integer>> bidFrequencies, Integer totalBids) {
        int numerator = 1 + bidFrequencies.get(issue).get(value);
        int denominator = domain.getValues(issue).size().intValue() + totalBids;
        return (double) numerator / (double) denominator;
    }

    private Tuple<Map<String, Map<Value, Double>>, Map<String, Map<Value, Double>>> updatedValueWeights() {
        HashMap<String, Map<Value, Double>> newValueWeights = new HashMap<>();
        HashMap<String, Map<Value, Double>> newEvalSums = new HashMap<>();

        for (String issue : domain.getIssues()) {
            newEvalSums.put(issue, new HashMap<>());

            Map<Value, Double> issueEvalSums = new HashMap<>();
            Map<Value, Double> issueValues = new HashMap<>();
            double denominator = 1.0;
            for (Value value : domain.getValues(issue)) {
                double evalSum = evalSums.get(issue).get(value);
                evalSum += omega(currentTimeBracket) * pRatio(issue, value, currentFrequencies, currentBidCount);
                issueEvalSums.put(value, evalSum);
                denominator = Math.max(denominator, evalSum);
            }
            for (Value value : domain.getValues(issue)) {
                double valNumerator = Math.pow(issueEvalSums.get(value) + 1, gamma);
                double valDenominator = Math.pow(denominator + 1, gamma);
                double valueWeight = valNumerator / valDenominator;
                issueValues.put(value, valueWeight);
            }
            newValueWeights.put(issue, issueValues);
            newEvalSums.put(issue, issueEvalSums);
        }
        Tuple<Map<String, Map<Value, Double>>, Map<String, Map<Value, Double>>> newMaps = new Tuple<>(newValueWeights, newEvalSums);
        return new Tuple<Map<String, Map<Value, Double>>, Map<String, Map<Value, Double>>>(newValueWeights, newEvalSums);
    }

    private Map<String, Double> updatedIssueWeights(Map<String, Map<Value, Double>> newValueWeights) {

        Set<String> issues = domain.getIssues();
        HashMap<String, Boolean> issuesUnchanged = new HashMap<>();
        HashMap<String, Double> newIssueWeights = new HashMap<>();
        boolean concession = false;

        for (String issue : issues) {
            ValueSet values = domain.getValues(issue);
            Map<Value, Double> issueVals = newValueWeights.get(issue);
            HashMap<Value, Double> oldFreqMap = new HashMap<>();
            HashMap<Value, Double> newFreqMap = new HashMap<>();
            long[] oldFreqVect = new long[values.size().intValue()];
            long[] newFreqVect = new long[values.size().intValue()];

            int i = 0;
            for (Value value : values) {
                double oldFreq = Freq(issue, value, previousFrequencies, previousBidCount);
                double newFreq = Freq(issue, value, currentFrequencies, currentBidCount);
                oldFreqMap.put(value, oldFreq);
                newFreqMap.put(value, newFreq);
                // Trying using just the numerator TODO: This is a change from the document
                oldFreqVect[i] = 1 + previousFrequencies.get(issue).get(value);
                newFreqVect[i] = 1 + currentFrequencies.get(issue).get(value);
            }

            ChiSquareTest chiSquareTest = new ChiSquareTest();
            double pVal = chiSquareTest.chiSquareDataSetsComparison(oldFreqVect, newFreqVect);
            if (pVal > 0.05) {
                issuesUnchanged.put(issue, true);
            } else {
                double averageUtilPrev = 0.0;
                double averageUtilCurr = 0.0;
                for (Value value : values) {
                    averageUtilPrev += issueVals.get(value) * oldFreqMap.get(value) / previousBidCount;
                    averageUtilCurr += issueVals.get(value) * newFreqMap.get(value) / currentBidCount;
                }
                if (averageUtilCurr < averageUtilPrev) {
                    concession = true;
                }
            }
        }

        if (concession) {
            for (String issue : domain.getIssues()) {
                if(!issuesUnchanged.get(issue)) {
                    double newWeight = issueWeights.get(issue) + delta(currentTimeBracket);
                    newIssueWeights.put(issue, newWeight);
                }
            }
        }

        //throw new IllegalStateException("Hello, just checking if I throw an error properly!!!");
        JFrame errorFrame = new JFrame("test error");
        errorFrame.setVisible(true);



        return newIssueWeights;
    }

    @Override
    public SmallTimeIndependentFreqModel with(Domain domain, Bid resBid) {
        if(domain == null) {
            throw new NullPointerException("domain is not initialized");
        }

        Map<String, Double> newIssueWeights = new HashMap<>();
        Map<String, Map<Value, Double>> newValueWeights = new HashMap<>();
        Map<String, Map<Value, Double>> newEvalSums = new HashMap<>();
        double newIssueWeightTotal = 0.0;
        for(String issue: domain.getIssues()) {
            double defaultWeight = 1.0;
            newIssueWeights.put(issue, defaultWeight);
            newIssueWeightTotal += defaultWeight;
            Map<Value, Double> issueValues = new HashMap<>();
            Map<Value, Double> issueEvalSum = new HashMap<>();
            for(Value value: domain.getValues(issue)) {
                issueValues.put(value, 1.0);
                issueEvalSum.put(value, 0.0);
            }
            newValueWeights.put(issue, issueValues);
            newEvalSums.put(issue, issueEvalSum);
        }

        return new SmallTimeIndependentFreqModel(domain, resBid, gamma, alpha, beta,
                timeBracketCount, newIssueWeightTotal, null, null,
                null, null, null, null, newEvalSums,
                newIssueWeights, newValueWeights);
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
        return "SmallTimeIndependentFreqModel" + (serial++) + "For" + domain;
    }

    @Override
    public Domain getDomain() {
        return domain;
    }

    @Override
    public SmallTimeIndependentFreqModel with(Action action, Progress progress) {
        checkDomainNull();
        if(!(action instanceof Offer)) {
            return this;
        }
        Bid bid = ((Offer) action).getBid();
        Double timeBracket = getCurrentTimeBracket(progress);

        Map<String, Map<Value, Double>> newValueWeights = valueWeights;
        Map<String, Double> newIssueWeights = issueWeights;
        Map<String, Map<Value, Double>> newEvalSums = evalSums;
        Map<String, Map<Value, Integer>> newCurrentFrequencies = currentFrequencies;
        Integer newCurrentBidCount = currentBidCount;
        Map<String, Map<Value, Integer>> newPreviousFrequencies = previousFrequencies;
        Integer newPreviousBidCount = previousBidCount;
        double newIssueWeightTotal = issueWeightTotal;
        Double newPreviousTimeBracket = previousTimeBracket;
        Double newCurrentTimeBracket = currentTimeBracket;

        if(!timeBracket.equals(currentTimeBracket)) {
            newPreviousTimeBracket = currentTimeBracket;
            newCurrentTimeBracket = timeBracket;
            newPreviousFrequencies = currentFrequencies;
            newCurrentFrequencies = new HashMap<>();
            for(String issue : domain.getIssues()) {
                newCurrentFrequencies.put(issue, new HashMap<>());
            }
            newPreviousBidCount = currentBidCount;
            newCurrentBidCount = 0;

            if(currentTimeBracket == null) {
                newValueWeights = valueWeights;
                newEvalSums = evalSums;
                newIssueWeights = issueWeights;
            }
            else if(previousTimeBracket == null) {
                Tuple<Map<String, Map<Value, Double>>, Map<String, Map<Value, Double>>> holder = updatedValueWeights();
                newValueWeights = holder.get1();
                newEvalSums = holder.get2();
                newIssueWeights = issueWeights;
            } else {
                Tuple<Map<String, Map<Value, Double>>, Map<String, Map<Value, Double>>> holder = updatedValueWeights();
                newValueWeights = holder.get1();
                newEvalSums = holder.get2();
                newIssueWeights = updatedIssueWeights(newValueWeights);
            }
            newIssueWeightTotal = 0.0;
            for(String issue: domain.getIssues()) {
                newIssueWeightTotal += newIssueWeights.get(issue);
            }
        }

        newCurrentFrequencies = addBid(bid, newCurrentFrequencies);
        newCurrentBidCount += 1;

        return new SmallTimeIndependentFreqModel(domain, resBid, gamma, alpha, beta, timeBracketCount,
                newIssueWeightTotal, newCurrentTimeBracket, newPreviousTimeBracket,
                newCurrentFrequencies, newCurrentBidCount, newPreviousFrequencies,
                newPreviousBidCount, newEvalSums, newIssueWeights,
                newValueWeights);
    }

    private Map<String, Map<Value, Integer>> addBid(Bid bid, Map<String, Map<Value, Integer>> bidFrequencies) {
        Map<String, Map<Value, Integer>> newBidFrequencies = cloneMap(bidFrequencies);

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

        return newBidFrequencies;
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
        return Objects.hash(getDomain(), resBid, gamma, alpha, beta, timeBracketCount, issueWeightTotal, currentTimeBracket, previousTimeBracket, currentFrequencies, currentBidCount, previousFrequencies, previousBidCount, evalSums, valueWeights);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SmallTimeIndependentFreqModel)) return false;
        SmallTimeIndependentFreqModel that = (SmallTimeIndependentFreqModel) o;
        return Double.compare(that.gamma, gamma) == 0 && Double.compare(that.alpha, alpha) == 0 && Double.compare(that.beta, beta) == 0 && timeBracketCount == that.timeBracketCount && Double.compare(that.issueWeightTotal, issueWeightTotal) == 0 && Objects.equals(getDomain(), that.getDomain()) && Objects.equals(resBid, that.resBid) && Objects.equals(currentTimeBracket, that.currentTimeBracket) && Objects.equals(previousTimeBracket, that.previousTimeBracket) && Objects.equals(evalSums, that.evalSums) && Objects.equals(currentFrequencies, that.currentFrequencies) && Objects.equals(currentBidCount, that.currentBidCount) && Objects.equals(previousFrequencies, that.previousFrequencies) && Objects.equals(previousBidCount, that.previousBidCount) && Objects.equals(issueWeights, that.issueWeights) && Objects.equals(valueWeights, that.valueWeights);
    }

    @Override
    public String toString() {
        return "LearningAgentOpponentModel[" + valueWeights + ", " + issueWeights + "]";
    }

    private void checkDomainNull() {
        if (domain == null) {
            throw new IllegalStateException("domain is not initialized");
        }
    }
}
