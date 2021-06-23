package geniusweb.exampleparties.learningagent; // TODO: change name

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import geniusweb.actions.FileLocation;

import java.util.UUID;
import geniusweb.actions.Accept;
import geniusweb.actions.Action;
import geniusweb.actions.LearningDone;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.bidspace.AllBidsList;
import geniusweb.inform.ActionDone;
import geniusweb.inform.Agreements;
import geniusweb.inform.Finished;
import geniusweb.inform.Inform;
import geniusweb.inform.Settings;
import geniusweb.inform.YourTurn;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.UtilitySpace;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import geniusweb.references.Parameters;
import tudelft.utilities.logging.Reporter;

import geniusweb.boa.*;

import com.fasterxml.jackson.databind.ObjectMapper;

public class LearningAgent extends DefaultBoa { // TODO: change name

    private Bid lastReceivedBid = null;
    private PartyId me;
    private final Random random = new Random();
    protected ProfileInterface profileint = null;
    private Progress progress;
    private String protocol;
    private Parameters parameters;
    private UtilitySpace utilitySpace;
    private PersistentState persistentState;
    private NegotiationData negotiationData;
    private List<File> dataPaths;
    private File persistentPath;
    private String opponentName;

    public LearningAgent() { // TODO: change name
    }

    public LearningAgent(Reporter reporter) { // TODO: change name
        super(reporter); // for debugging
    }

    /**
     * This method mostly contains utility functionallity for the agent to function
     * properly. The code that is of most interest for the ANL competition is
     * further below and in the other java files in this directory. It does,
     * however, not hurt to read through this code to have a better understanding of
     * what is going on.
     * 
     * @param info information object for agent
     */
    @Override
    public void notifyChange(Inform info) {
        try {
            if ("Learn".equals(protocol)) {
                // We are in the learning step: We execute the learning and notify when we are
                // done. REMEMBER that there is a deadline of 60 seconds for this step.
                learn();
                getConnection().send(new LearningDone(me));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle learning step info", e);
        }
        super.notifyChange(info);
    }

    /** Let GeniusWeb know what protocols that agent is capable of handling */
    @Override
    public Capabilities getCapabilities() {
        return new Capabilities(new HashSet<>(Arrays.asList("SAOP", "Learn")), Collections.singleton(Profile.class));
    }

    /** Terminate agent */
    @Override
    public void terminate() {
        super.terminate();
        if (this.profileint != null) {
            this.profileint.close();
            this.profileint = null;
        }
    }

    /*
     * *****************************NOTE:************************************
     * Everything below this comment is most relevant for the ANL competition.
     * **********************************************************************
     */

    /** Provide a description of the agent */
    @Override
    public String getDescription() {
        return "This is the example party of ANL 2021. It can handle the Learn protocol and learns simple characteristics of the opponent.";
    }

    /**
     * Processes an Action performed by the opponent.
     * 
     * @param action
     */
    private void processAction(Action action) {
        if (action instanceof Offer) {
            // If the action was an offer: Obtain the bid and add it's value to our
            // negotiation data.
            this.lastReceivedBid = ((Offer) action).getBid();
            this.negotiationData.addBidUtil(this.utilitySpace.getUtility(this.lastReceivedBid).doubleValue());
        }
    }

    /**
     * This method is called when the negotiation has finished. It can process the
     * final agreement.
     * 
     * @param agreements
     */
    private void processAgreements(Agreements agreements) {
        // Check if we reached an agreement (walking away or passing the deadline
        // results in no agreement)
        if (!agreements.getMap().isEmpty()) {
            // Get the bid that is agreed upon and add it's value to our negotiation data
            Bid agreement = agreements.getMap().values().iterator().next();
            this.negotiationData.addAgreementUtil(this.utilitySpace.getUtility(agreement).doubleValue());
        }
    }

    /**
     * send our next offer
     */
    private void myTurn() throws IOException {
        Action action;
        if (isGood(lastReceivedBid)) {
            // If the last received bid is good: create Accept action
            action = new Accept(me, lastReceivedBid);
        } else {
            // Obtain list of all bids
            AllBidsList bidspace = new AllBidsList(this.utilitySpace.getDomain());
            Bid bid = null;

            // Iterate randomly through list of bids until we find a good bid
            for (int attempt = 0; attempt < 500 && !isGood(bid); attempt++) {
                long i = random.nextInt(bidspace.size().intValue());
                bid = bidspace.get(BigInteger.valueOf(i));
            }

            // Create offer action
            action = new Offer(me, bid);
        }

        // Send action
        getConnection().send(action);
    }

    /**
     * The method checks if a bid is good.
     * 
     * @param bid the bid to check
     * @return true iff bid is good for us.
     */
    private boolean isGood(Bid bid) {
        if (bid == null)
            return false;

        // Check if we already know the opponent
        if (this.persistentState.knownOpponent(this.opponentName)) {
            // Obtain the average of the max utility that the opponent has offered us in
            // previous negotiations.
            Double avgMaxUtility = this.persistentState.getAvgMaxUtility(this.opponentName);

            // Request 5% more than the average max utility offered by the opponent.
            return this.utilitySpace.getUtility(bid).doubleValue() > (avgMaxUtility * 1.05);
        }

        // Check a simple business rule
        Boolean nearDeadline = progress.get(System.currentTimeMillis()) > 0.95;
        Boolean acceptable = this.utilitySpace.getUtility(bid).doubleValue() > 0.7;
        Boolean good = this.utilitySpace.getUtility(bid).doubleValue() > 0.9;
        return (nearDeadline && acceptable) || good;
    }

    /**
     * This method is invoked if the learning phase is started. There is now time to
     * process previously stored data and use it to update our persistent state.
     * This persistent state is passed to the agent again in future negotiation
     * session. REMEMBER that there is a deadline of 60 seconds for this step.
     */
    private void learn() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Iterate through the negotiation data file paths
        for (File dataPath : this.dataPaths) {
            NegotiationData negotiationData;
            try {
                // Load the negotiation data object of a previous negotiation
                negotiationData = objectMapper.readValue(dataPath, NegotiationData.class);
            } catch (IOException e) {
                throw new RuntimeException("Negotiation data provided to learning step does not exist", e);
            }

            // Process the negotiation data in our persistent state
            this.persistentState.update(negotiationData);
        }

        // Write the persistent state object to file
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.persistentPath, this.persistentState);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write persistent state to disk", e);
        }
    }
}
