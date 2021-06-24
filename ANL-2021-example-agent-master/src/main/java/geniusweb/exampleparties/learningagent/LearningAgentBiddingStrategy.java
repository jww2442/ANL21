package geniusweb.exampleparties.learningagent;

import geniusweb.actions.Action;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.boa.BoaState;
import geniusweb.boa.biddingstrategy.BiddingStrategy;
import geniusweb.boa.biddingstrategy.ExtendedUtilSpace;
import geniusweb.issuevalue.Bid;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.LinearAdditive;

import java.util.List;


public class LearningAgentBiddingStrategy implements BiddingStrategy {
    // bidSpace=null means we're not yet initialized.
    private ExtendedUtilSpace bidSpace = null;
    private PartyId me;

    @Override
    public Action getAction(BoaState state) {
        if (bidSpace == null) {
            init(state);
        }
        //TODO Choose Action here
        return null;
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

        this.bidSpace = getBidSpace(profile);
        //TODO: Assign private vars here

    }

    protected ExtendedUtilSpace getBidSpace(LinearAdditive profile) {
        return new ExtendedUtilSpace(profile);
    }

}
