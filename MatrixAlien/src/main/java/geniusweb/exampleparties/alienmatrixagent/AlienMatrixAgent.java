package geniusweb.exampleparties.alienmatrixagent; // TODO: change name

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import geniusweb.actions.FileLocation;

import java.util.UUID;

import geniusweb.actions.Action;
import geniusweb.actions.LearningDone;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.boa.acceptancestrategy.AcceptanceStrategy;
import geniusweb.boa.acceptancestrategy.TimeDependentAcceptanceStrategy;
import geniusweb.boa.biddingstrategy.BiddingStrategy;
import geniusweb.boa.biddingstrategy.TimeDependentBiddingStrategy;
import geniusweb.exampleparties.alienmatrixagent.OurStochasticAcceptanceStrategy;
import geniusweb.exampleparties.alienmatrixagent.OurStochasticBiddingStrategy;
import geniusweb.inform.ActionDone;
import geniusweb.inform.Agreements;
import geniusweb.inform.Finished;
import geniusweb.inform.Inform;
import geniusweb.inform.Settings;
import geniusweb.inform.YourTurn;
import geniusweb.issuevalue.Bid;
import geniusweb.opponentmodel.FrequencyOpponentModel;
import geniusweb.opponentmodel.OpponentModel;
import geniusweb.party.Capabilities;
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

import javax.swing.*;

public class AlienMatrixAgent extends DefaultBoa { // TODO: change name

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

    public AlienMatrixAgent() { // TODO: change name
    }

    public AlienMatrixAgent(Reporter reporter) { // TODO: change name
        super(reporter); // for debugging
    }

    /**
     * This method mostly contains utility functionality for the agent to function
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
            if (info instanceof Settings) {
                // info is a Settings object that is passed at the start of a negotiation
                Settings settings = (Settings) info;

                // ID of my agent
                this.me = settings.getID();

                // The progress object keeps track of the deadline
                this.progress = settings.getProgress();

                // Protocol that is initiate for the agent
                this.protocol = settings.getProtocol().getURI().getPath();

                // Parameters for the agent (can be passed through the GeniusWeb GUI, or a
                // JSON-file)
                this.parameters = settings.getParameters();

                // The PersistentState is loaded here (see 'PersistenData,java')
                if (this.parameters.containsKey("persistentstate"))
                    this.persistentPath = new FileLocation(
                            UUID.fromString((String) this.parameters.get("persistentstate"))).getFile();
                if (this.persistentPath != null && this.persistentPath.exists()) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    this.persistentState = objectMapper.readValue(this.persistentPath, PersistentState.class);
                } else {
                    this.persistentState = new PersistentState();
                }

                // The negotiation data paths are converted here from List<String> to List<File>
                // for improved usage. For safety reasons, this is more comprehensive than
                // normally.
                if (this.parameters.containsKey("negotiationdata")) {
                    List<String> dataPaths_raw = (List<String>) this.parameters.get("negotiationdata");
                    this.dataPaths = new ArrayList<>();
                    for (String path : dataPaths_raw)
                        this.dataPaths.add(new FileLocation(UUID.fromString(path)).getFile());
                }
                if ("Learn".equals(protocol)) {
                    // We are in the learning step: We execute the learning and notify when we are
                    // done. REMEMBER that there is a deadline of 60 seconds for this step.
                    learn();
                    getConnection().send(new LearningDone(me));
                } else {
                    // We are in the negotiation step.

                    // Create a new NegotiationData object to store information on this negotiation.
                    // See 'NegotiationData.java'.
                    this.negotiationData = new NegotiationData();

                    // Obtain our utility space, i.e. the problem we are negotiating and our
                    // preferences over it.
                    try {
                        this.profileint = ProfileConnectionFactory.create(settings.getProfile().getURI(),
                                getReporter());
                        this.utilitySpace = ((UtilitySpace) profileint.getProfile());
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            } else if (info instanceof ActionDone) {
                // The info object is an action that is performed by an agent.
                Action action = ((ActionDone) info).getAction();

                // Check if this is not our own action
                if (!this.me.equals(action.getActor())) {
                    // Check if we already know who we are playing against.
                    if (this.opponentName == null) {
                        // The part behind the last _ is always changing, so we must cut it off.
                        String fullOpponentName = action.getActor().getName();
                        int index = fullOpponentName.lastIndexOf("_");
                        this.opponentName = fullOpponentName.substring(0, index);

                        // Add name of the opponent to the negotiation data
                        this.negotiationData.setOpponentName(this.opponentName);
                    }
                    // Process the action of the opponent.
                    processAction(action);
                }
            } else if (info instanceof YourTurn) {
                // Advance the round number if a round-based deadline is set.
                if (progress instanceof ProgressRounds) {
                    progress = ((ProgressRounds) progress).advance();
                }

                // The info notifies us that it is our turn
                //myTurn(); //TODO: My Turn now no longer necessary? Instead Call super.notifyChange(info)
            } else if (info instanceof Finished) {
                // The info is a notification that th negotiation has ended. This Finished
                // object also contains the final agreement (if any).
                Agreements agreements = ((Finished) info).getAgreement();
                processAgreements(agreements);

                // Write the negotiation data that we collected to the path provided.
                if (this.dataPaths != null && this.negotiationData != null) {
                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.dataPaths.get(0),
                                this.negotiationData);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write negotiation data to disk", e);
                    }
                }

                // Log the final outcome and terminate
                getReporter().log(Level.INFO, "Final outcome:" + info);
                terminate();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle info", e);
        }
        if(!"Learn".equals(protocol)) {
            try {
                super.notifyChange(info);
            } catch (Exception e) {
                if (this.dataPaths != null && this.negotiationData != null) {
                    try {
                        File f = this.dataPaths.get(0);
                        FileWriter fw = new FileWriter(f);
                        fw.write(e.getMessage());


                    } catch (IOException ioe) {
                        throw new RuntimeException("Failed to write negotiation error to disk", ioe);
                    }
                }
                throw new RuntimeException("Error printed in temp data e");
            }
        }
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

    /** Provide [vectorized_songs[i:i+batch_size] for i in idx]a description of the agent */
    @Override
    public String getDescription() {
        return "This is the example party of ANL 2021. It can handle the Learn protocol and learns simple characteristics of the opponent.";
    }

    @Override
    protected Class<? extends OpponentModel> getOpponentModel(Settings settings)
            throws InstantiationFailedException {
         return FrequencyOpponentModel.class; //TODO: Choose an Opponent Model
         //return TimeIndependentFreqModel.class;
        //return SmallTimeIndependentFreqModel.class;
    }

    @Override
    protected BiddingStrategy getBiddingStrategy(Settings settings)
            throws InstantiationFailedException {
        //return new TimeDependentBiddingStrategy(); //TODO: Choose a Bidding Strategy
        return new OurTimeDependentBiddingStrategy();
        //return new OurStochasticBiddingStrategy();
    }

    @Override
    protected AcceptanceStrategy getAccceptanceStrategy(Settings settings)
            throws InstantiationFailedException {
        //return new TimeDependentAcceptanceStrategy(); //TODO: Choose an Acceptance Strategy
        return new OurTimeDependentAcceptanceStrategy();
        //return new OurStochasticAcceptanceStrategy();
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
