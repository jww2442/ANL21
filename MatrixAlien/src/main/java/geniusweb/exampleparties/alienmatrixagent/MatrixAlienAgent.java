package geniusweb.exampleparties.alienmatrixagent; // TODO: change name

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

import geniusweb.actions.*;

import geniusweb.actions.Action;
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

public class MatrixAlienAgent extends DefaultParty {

    private Bid lastReceivedBid = null;
    private PartyId me;
    private PartyId them;
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

    private Class<? extends OpponentModel> opModelClass;
    private ExpandedStrategy expandedStrategy;
    private List<Action> actionHistory;
    private Profile profile;
    private Settings settings;

    private Reporter reporter;

    private final boolean doLearnE = true;
    private final boolean doLearnMin = true;
    public static final Double initial_E = 0.00033;
    public static final String initial_D = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    public static final Double initial_min = 0.5;

    public MatrixAlienAgent() {
    }

    public MatrixAlienAgent(Reporter reporter) {
        super(reporter); // for debugging
        this.reporter = reporter;
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
            if (info instanceof Settings) { // SETTINGS*********SETTINGS**********SETTINGS*********SETTINGS**********
                // info is a Settings object that is passed at the start of a negotiation
                Settings settings = (Settings) info;
                this.settings = settings;

                Class<? extends OpponentModel> omClass = getOpponentModel(settings);
                opModelClass = omClass;
                actionHistory = new ArrayList<>();


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
                        this.profile = profileint.getProfile();
                        ExpandedStrategy expandStrat = getExpandedStrategy(settings, profile, reporter);
                        expandedStrategy = expandStrat;
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                } // END SETTINGS*****END SETTINGS******END SETTINGS*****END SETTINGS**********
            } else if (info instanceof ActionDone) { //ACTIONDONE********ACTIONDONE********ACTIONDONE********
                // The info object is an action that is performed by an agent.
                Action action = ((ActionDone) info).getAction();
                List<Action> newactions = new ArrayList<Action>(actionHistory);
                newactions.add(action);
                actionHistory = newactions;
                if(action instanceof Offer) {
                    expandedStrategy.countBid(((Offer) action).getBid(), !this.me.equals(action.getActor())); //Regardless of who offers the bid.
                }

                // Check if this is not our own action
                if (!this.me.equals(action.getActor())) { //THEIR ACTIONDONE********THEIR ACTIONDONE**********
                    // Check if we already know who we are playing against.
                    if (this.opponentName == null) {
                        // The part behind the last _ is always changing, so we must cut it off.
                        String fullOpponentName = action.getActor().getName();
                        int index = fullOpponentName.lastIndexOf("_");
                        this.opponentName = fullOpponentName.substring(0, index);
                        this.them = action.getActor();

                        // Add name of the opponent to the negotiation data
                        this.negotiationData.setOpponentName(this.opponentName);

                        if(doLearnE) {
                            expandedStrategy.init2electricBoogaloo(persistentState.getOpponentEVal(opponentName));

                        }
                        else {
                            expandedStrategy.init2electricBoogaloo(null);
                        }
                        if(doLearnMin){
                            expandedStrategy.init3(persistentState.getOpponentMinVal(opponentName));
                        } else {
                            expandedStrategy.init3(null);
                        }

                    }
                    // Process the action of the opponent.
                    processAction(action);

                }
                else { //OUR ACTIONDONE**********OUR ACTIONDONE**************OUR ACTIONDONE**************
                    //This is action we have done
                }
                //END ACTIONDONE***************END ACTIONDONE*************ENDACTIONDONE**************END ACTIONDONE
            } else if (info instanceof YourTurn) {//YOURTURN***********YOURTURN*****************YOURTURN**********
                // Advance the round number if a round-based deadline is set.
                if (progress instanceof ProgressRounds) {
                    progress = ((ProgressRounds) progress).advance();
                }

                // The info notifies us that it is our turn
                //myTurn();
                getConnection().send(getAction());
                //END YOURTURN*************END YOURTURN**************END YOURTURN******************
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
    }

    public Action getAction() {
        //DO STUFF

        Bid lastBid = getLastBid();
        if(lastBid != null && expandedStrategy.isAcceptable(lastBid, progress)) {
            return new Accept(me, lastBid);
        }

        return expandedStrategy.getAction(progress, null); //Should be ok if we use only UtilitySpace Opponent Models
    }

    public Bid getLastBid() {
        List<Action> actions = actionHistory;
        for(int n = actions.size() - 1; n >= 0; n--) {
            Action action = actions.get(n);
            if(action instanceof Offer) {
                return ((Offer) action).getBid();
            }
        }
        return null;
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
        String alienMatrixMatrixAlienMatrixAlienAlienMatrixMatrixAlienAlienMatrixAlienMatrixMatrixAlienMatrixAlienAlienMatrixAlienMatrixMatrixAlienAlienMatrixMatrixAlienMatrixAlienAlienMatrix =
                "This is the University of Tulsa MASTERS submission for the 2021 ANL competition. " +
                "It can adapt its boulware constant and min value depending on its success during previous rounds of negotiation. It also has a super cool name.";
        return alienMatrixMatrixAlienMatrixAlienAlienMatrixMatrixAlienAlienMatrixAlienMatrixMatrixAlienMatrixAlienAlienMatrixAlienMatrixMatrixAlienAlienMatrixMatrixAlienMatrixAlienAlienMatrix;
    }

    protected Class<? extends OpponentModel> getOpponentModel(Settings settings)
            throws InstantiationFailedException {
        return FrequencyOpponentModel.class;
        //return TimeIndependentFreqModel.class;
        //return SmallTimeIndependentFreqModel.class;
    }

    protected ExpandedStrategy getExpandedStrategy(Settings settings, Profile profile, Reporter reporter) {
        return new ExpandedStrategy(settings, profile, reporter);
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

        //pass eval from p-state to neg-data
        Double eval = persistentState.getOpponentEVal(opponentName);
        if(eval == null){
            this.negotiationData.seteVal(this.initial_E);
        } else {
            this.negotiationData.seteVal(eval);
        }
        Double min = persistentState.getOpponentMinVal(opponentName);
        if(min == null){
            this.negotiationData.setMinVal(this.initial_min);
        } else {
            this.negotiationData.setMinVal(min);
        }

        Integer encounters = persistentState.getOpponentEncounters(opponentName);
        if(encounters == null){
            this.negotiationData.setNumEncounters(0);
        } else {
            this.negotiationData.setNumEncounters(encounters);
        }

        // Check if we reached an agreement (walking away or passing the deadline
        // results in no agreement)
        if (!agreements.getMap().isEmpty()) {
            // Get the bid that is agreed upon and add it's value to our negotiation data
            Bid agreement = agreements.getMap().values().iterator().next();
            this.negotiationData.addAgreementUtil(this.utilitySpace.getUtility(agreement).doubleValue());
        }

        this.negotiationData.setTimeTaken(settings.getProgress().get(System.currentTimeMillis()));
        this.negotiationData.changeEandMin();
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