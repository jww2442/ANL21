package geniusweb.exampleparties.learningagent;

import geniusweb.actions.Action;
import geniusweb.actions.Offer;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.opponentmodel.OpponentModel;
import geniusweb.profile.utilityspace.UtilitySpace;
import geniusweb.progress.Progress;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

// FIXME: MOST OF THIS IS A COPY OF FREQUENCY OPPONENT MODEL. MUCH SHOULD BE REPLACED

public class LearningAgentOpponentModel implements UtilitySpace, OpponentModel {
    private static final int DECIMALS = 4; // accuracy of our computations.
    private static int serial = 1; // counter for auto name generation

    private final Domain domain;
    private final Map<String, Map<Value, Integer>> bidFrequencies;
    private final BigDecimal totalBids;
    private final Bid resBid;

    public LearningAgentOpponentModel() {
        this.domain = null;
        this.bidFrequencies = null;
        this.totalBids = BigDecimal.ZERO;
        this.resBid = null;
    }

    protected LearningAgentOpponentModel(Domain domain, Map<String, Map<Value, Integer>> freqs,
                                         BigDecimal total, Bid resBid) {
        checkDomainNull();
        this.domain = domain;
        this.bidFrequencies = freqs;
        this.totalBids = total;
        this.resBid = resBid;
    }

    @Override
    public LearningAgentOpponentModel with(Domain domain, Bid resBid) {
        checkDomainNull();
        // FIXME merge already available frequencies?
        return new LearningAgentOpponentModel(domain,
                domain.getIssues().stream().collect(
                        Collectors.toMap(iss -> iss, iss -> new HashMap<>())),
                BigDecimal.ZERO, resBid);
    }

    @Override
    public BigDecimal getUtility(Bid bid) {
        checkDomainNull();
        if (totalBids == BigDecimal.ZERO) {
            return BigDecimal.ONE;
        }
        //TODO: Implement getUtility
        return null;
    }

    @Override
    public String getName() {
        checkDomainNull();
        return "LearningAgentOppModel" + (serial++) + "For" + domain;
    }

    @Override
    public Domain getDomain() {
        return domain;
    }

    @Override
    public LearningAgentOpponentModel with(Action action, Progress progress) {
        checkDomainNull();

        if(!(action instanceof Offer)) {
            return this;
        }
        Bid bid = ((Offer) action).getBid();
        Map<String, Map<Value, Integer>> newFreqs = cloneMap(bidFrequencies);
        for (String issue : domain.getIssues()) {
            Map<Value, Integer> freqs = newFreqs.get(issue);
            Value value = bid.getValue(issue);
            if(value != null) {
                Integer oldfreq = freqs.get(value);
                if(oldfreq == null) {
                    oldfreq = 0;
                }
                freqs.put(value, oldfreq + 1);
            }
        }

        return new LearningAgentOpponentModel(domain, newFreqs,
                totalBids.add(BigDecimal.ONE), resBid);
    }

    public Map<Value, Integer> getCounts(String issue) {
        checkDomainNull();
        if (!(bidFrequencies.containsKey(issue))) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(bidFrequencies.get(issue));
    }

    private BigDecimal getFraction(String issue, Value value) {
        if (totalBids == BigDecimal.ZERO) {
            return BigDecimal.ONE;
        }
        Integer freq = bidFrequencies.get(issue).get(value);
        if (freq == null) {
            freq = 0;
        }
        return new BigDecimal(freq).divide(totalBids, DECIMALS,
                BigDecimal.ROUND_HALF_UP);
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
        final int prime = 31;
        int result = 1;
        result = prime * result + ((bidFrequencies == null) ? 0: bidFrequencies.hashCode());
        result = prime * result + ((domain == null) ? 0 : domain.hashCode());
        result = prime * result + ((totalBids == null) ? 0 : totalBids.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LearningAgentOpponentModel other = (LearningAgentOpponentModel) obj;
        if (bidFrequencies == null) {
            if (other.bidFrequencies != null)
                return false;
        } else if (!bidFrequencies.equals(other.bidFrequencies))
            return false;
        if (domain == null) {
            if (other.domain != null)
                return false;
        } else if (!domain.equals(other.domain))
            return false;
        if (totalBids == null) {
            if (other.totalBids != null)
                return false;
        } else if (!totalBids.equals(other.totalBids))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "LearningAgentOpponentModel[" + totalBids +  "," + bidFrequencies + "]";
    }

    private void checkDomainNull() {
        if (domain == null) {
            throw new IllegalStateException("domain is not initialized");
        }
    }
}
