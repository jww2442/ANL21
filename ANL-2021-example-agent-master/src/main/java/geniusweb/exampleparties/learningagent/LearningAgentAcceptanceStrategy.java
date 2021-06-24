package geniusweb.exampleparties.learningagent;

import geniusweb.bidspace.BidsWithUtility;
import geniusweb.boa.BoaState;
import geniusweb.boa.acceptancestrategy.AcceptanceStrategy;
import geniusweb.issuevalue.Bid;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.LinearAdditive;

public class LearningAgentAcceptanceStrategy implements AcceptanceStrategy {

    @Override
    public Boolean isAcceptable(Bid bid, BoaState state) {
        return null;
    }

    protected BidsWithUtility getBidspace(Profile profile) {
        return new BidsWithUtility((LinearAdditive) profile);
    }
}
