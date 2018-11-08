package org.adaptlab.chpir.android.survey;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.activeandroid.Model;
import com.crashlytics.android.Crashlytics;

import org.adaptlab.chpir.android.survey.location.LocationManager;
import org.adaptlab.chpir.android.survey.models.Display;
import org.adaptlab.chpir.android.survey.models.DisplayInstruction;
import org.adaptlab.chpir.android.survey.models.FollowUpQuestion;
import org.adaptlab.chpir.android.survey.models.Instrument;
import org.adaptlab.chpir.android.survey.models.LoopQuestion;
import org.adaptlab.chpir.android.survey.models.MultipleSkip;
import org.adaptlab.chpir.android.survey.models.Option;
import org.adaptlab.chpir.android.survey.models.Question;
import org.adaptlab.chpir.android.survey.models.Response;
import org.adaptlab.chpir.android.survey.models.Score;
import org.adaptlab.chpir.android.survey.models.ScoreScheme;
import org.adaptlab.chpir.android.survey.models.Survey;
import org.adaptlab.chpir.android.survey.questionfragments.MultipleSelectMultipleQuestionsFragment;
import org.adaptlab.chpir.android.survey.questionfragments.SingleSelectMultipleQuestionsFragment;
import org.adaptlab.chpir.android.survey.roster.RosterActivity;
import org.adaptlab.chpir.android.survey.rules.InstrumentSurveyLimitPerMinuteRule;
import org.adaptlab.chpir.android.survey.rules.InstrumentSurveyLimitRule;
import org.adaptlab.chpir.android.survey.rules.InstrumentTimingRule;
import org.adaptlab.chpir.android.survey.rules.RuleBuilder;
import org.adaptlab.chpir.android.survey.utils.AppUtil;
import org.adaptlab.chpir.android.survey.utils.AuthUtils;
import org.adaptlab.chpir.android.survey.utils.LocaleManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.fabric.sdk.android.Fabric;

import static org.adaptlab.chpir.android.survey.utils.FormatUtils.isEmpty;

public class SurveyFragment extends Fragment {
    public final static String EXTRA_INSTRUMENT_ID = "org.adaptlab.chpir.android.survey" +
            ".instrument_id";
    public final static String EXTRA_QUESTION_NUMBER = "org.adaptlab.chpir.android.survey" +
            ".question_number";
    public final static String EXTRA_SURVEY_ID = "org.adaptlab.chpir.android.survey.survey_id";
    public final static String EXTRA_PREVIOUS_QUESTION_IDS = "org.adaptlab.chpir.android.survey" +
            ".previous_questions";
    public final static String EXTRA_PARTICIPANT_METADATA = "org.adaptlab.chpir.android.survey" +
            ".metadata";
    public final static String EXTRA_QUESTIONS_TO_SKIP_IDS = "org.adaptlab.chpir.android.survey" +
            ".questions_to_skip_ids";
    public final static String EXTRA_SECTION_ID = "org.adaptlab.chpir.android.survey.section_id";
    public final static String EXTRA_AUTHORIZE_SURVEY = "org.adaptlab.chpir.android.survey" +
            ".authorize_boolean";
    public final static String EXTRA_DISPLAY_NUMBER = "org.adaptlab.chpir.android.survey" +
            ".display_number";
    private static final String TAG = "SurveyFragment";
    private static final int REVIEW_CODE = 100;
    public static final int AUTHORIZE_CODE = 300;
    private static final int ACCESS_FINE_LOCATION_CODE = 1;
    public static final int OFFSET = 1000000;

    private LinearLayout mQuestionViewLayout;
    private Instrument mInstrument;
    private Survey mSurvey;
    private String mMetadata;
    private ArrayList<QuestionFragment> mQuestionFragments;
    private ArrayList<Display> mDisplays;
    private HashMap<Question, Response> mResponses;
    private HashMap<Question, List<Option>> mOptions;
    private HashMap<Display, List<Question>> mDisplayQuestions;
    private HashMap<String, List<Question>> mQuestionsToSkipMap;
    private HashMap<Long, List<Option>> mSpecialOptions;
    private HashMap<Display, List<DisplayInstruction>> mDisplayInstructions;
    private HashSet<String> mQuestionsToSkipSet;
    private TextView mDisplayIndexLabel;
    private TextView mParticipantLabel;
    private ProgressBar mProgressBar;
    private ProgressBar mIndeterminateProgressBar;
    private Display mDisplay;
    private int mDisplayNumber;
    private ArrayList<Integer> mPreviousDisplays;
    private LocationManager mLocationManager;
    private NestedScrollView mScrollView;
    private TextView mDisplayTitle;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private boolean mNavDrawerSet = false;
    private boolean isActivityFinished = false;
    private boolean isScreenRotated = false;
    private ExpandableListView mExpandableListView;
    private List<String> mExpandableListTitle;
    private LinkedHashMap<String, List<String>> mExpandableListData;

    public void refreshView() {
        AuthorizedActivity authority = (AuthorizedActivity) getActivity();
        if (authority != null && authority.getAuthorize() && AppUtil.getAdminSettingsInstance() != null && AppUtil
                .getAdminSettingsInstance().getRequirePassword() && !AuthUtils.isSignedIn()) {
            authority.setAuthorize(false);
            Intent i = new Intent(getContext(), LoginActivity.class);
            getActivity().startActivityForResult(i, AUTHORIZE_CODE);
        } else {
            refreshUIComponents();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == REVIEW_CODE) {
            int displayNum = data.getExtras().getInt(EXTRA_DISPLAY_NUMBER);
            if (displayNum == Integer.MIN_VALUE) {
                checkForCriticalResponses();
            } else {
                mDisplay = mDisplays.get(displayNum);
                mDisplayNumber = displayNum;
                if (mDisplay != null) {
                    createQuestionFragments();
                } else {
                    checkForCriticalResponses();
                }
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        setHasOptionsMenu(true);
        if (AppUtil.getContext() == null) AppUtil.setContext(getActivity());
        boolean authority = getActivity().getIntent().getBooleanExtra(EXTRA_AUTHORIZE_SURVEY,
                false);
        if (authority) {
            AuthorizedActivity authorizedActivity = (AuthorizedActivity) getActivity();
            authorizedActivity.setAuthorize(true);
        }
        if (savedInstanceState != null) {
            mInstrument = Instrument.findByRemoteId(savedInstanceState.getLong
                    (EXTRA_INSTRUMENT_ID));
            if (!checkRules()) {
                finishActivity();
            }
            launchRosterSurvey();
            if (!mInstrument.isRoster()) {
                mSurvey = Survey.load(Survey.class, savedInstanceState.getLong(EXTRA_SURVEY_ID));
            }
            mDisplayNumber = savedInstanceState.getInt(EXTRA_DISPLAY_NUMBER);
        } else {
            Long instrumentId = getActivity().getIntent().getLongExtra(EXTRA_INSTRUMENT_ID, -1);
            mMetadata = getActivity().getIntent().getStringExtra(EXTRA_PARTICIPANT_METADATA);
            if (instrumentId == -1) return;
            mInstrument = Instrument.findByRemoteId(instrumentId);
            if (mInstrument == null) return;
            if (!checkRules()) {
                finishActivity();
            }
            launchRosterSurvey();
            if (!mInstrument.isRoster()) {
                loadOrCreateSurvey();
            }
            if (mSurvey != null && mSurvey.getLastQuestion() != null &&
                    mSurvey.getLastQuestion().getDisplay() != null) {
                mDisplayNumber = mSurvey.getLastQuestion().getDisplay().getPosition() - 1;
            } else {
                mDisplayNumber = 0;
            }
        }
        mDisplays = (ArrayList<Display>) mInstrument.displays();
        mDisplay = mDisplays.get(mDisplayNumber);
        mPreviousDisplays = new ArrayList<>();
        mQuestionFragments = new ArrayList<>();
        mQuestionsToSkipMap = new HashMap<>();
        mQuestionsToSkipSet = new HashSet<>();
        mSpecialOptions = new HashMap<>();
        mDisplayQuestions = new HashMap<>();
        mResponses = new HashMap<>();
        mOptions = new HashMap<>();
        new InstrumentDataTask().execute(mInstrument, mSurvey);
        registerCrashlytics();
    }

    private void requestLocationUpdates() {
        if (AppUtil.getAdminSettingsInstance().getRecordSurveyLocation()) {
            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission
                    .ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest
                        .permission.ACCESS_FINE_LOCATION}, ACCESS_FINE_LOCATION_CODE);
            }
        }
    }

    private void finishActivity() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getActivity().finishAfterTransition();
        } else {
            getActivity().finish();
        }
    }

    private void registerCrashlytics() {
        if (AppUtil.PRODUCTION) {
            Fabric.with(getActivity(), new Crashlytics());
            Crashlytics.setString(getString(R.string.last_instrument), mInstrument.getTitle());
            Crashlytics.setString(getString(R.string.last_survey), mSurvey.getUUID());
            Crashlytics.setString(getString(R.string.last_display), mDisplay.getTitle());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case ACCESS_FINE_LOCATION_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager
                        .PERMISSION_GRANTED) {
                    startLocationUpdates();
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_survey, parent, false);
        mDisplayTitle = (TextView) v.findViewById(R.id.display_title);
        mQuestionViewLayout = (LinearLayout) v.findViewById(R.id.question_component_layout);
        mParticipantLabel = (TextView) v.findViewById(R.id.participant_label);
        mDisplayIndexLabel = (TextView) v.findViewById(R.id.display_index_label);
        mProgressBar = (ProgressBar) v.findViewById(R.id.progress_bar);
        mIndeterminateProgressBar = (ProgressBar) v.findViewById(R.id.indeterminateProgressBar);
        ActivityCompat.invalidateOptionsMenu(getActivity());
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) actionBar.setTitle(mInstrument.getTitle());
        mScrollView = (NestedScrollView) v.findViewById(R.id.survey_fragment_scroll_view);
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        requestLocationUpdates();
    }

    private void startLocationUpdates() {
        if (mLocationManager == null) {
            mLocationManager = new LocationManager(getActivity());
            mLocationManager.startLocationUpdates();
        }
    }

    public LocationManager getLocationManager() {
        if (mLocationManager == null) startLocationUpdates();
        return mLocationManager;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mDisplayQuestions.size() > 0) {
            refreshView();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_DISPLAY_NUMBER, mDisplayNumber);
        outState.putLong(EXTRA_INSTRUMENT_ID, mInstrument.getRemoteId());
        outState.putLong(EXTRA_SURVEY_ID, mSurvey.getId());
        isScreenRotated = true;
    }

    @Override
    public void onStop() {
        if (mLocationManager != null) {
            mLocationManager.stopLocationUpdates();
        }
        super.onStop();
    }

    private LinkedHashMap<String, List<String>> getListData() {
        LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < mDisplays.size(); i++) {
            List<String> displayTitles = map.get(mDisplays.get(i).getSectionTitle());
            if (displayTitles == null) displayTitles = new ArrayList<>();
            displayTitles.add(mDisplays.get(i).getTitle());
            map.put(mDisplays.get(i).getSectionTitle(), displayTitles);
        }
        return map;
    }

    private void setDrawerItems() {
        final ActionBar mActionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        ExpandableListAdapter mExpandableListAdapter = new DisplayTitlesListAdapter(getContext(),
                mExpandableListTitle, mExpandableListData);
        mExpandableListView.setAdapter(mExpandableListAdapter);
        mExpandableListView.setOnGroupExpandListener(new ExpandableListView.OnGroupExpandListener() {
            @Override
            public void onGroupExpand(int groupPosition) {
                mActionBar.setTitle(mExpandableListTitle.get(groupPosition));
            }
        });

        mExpandableListView.setOnGroupCollapseListener(new ExpandableListView.OnGroupCollapseListener() {
            @Override
            public void onGroupCollapse(int groupPosition) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(mExpandableListTitle.get(groupPosition));
            }
        });

        mExpandableListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                String selectedItem = ((List) (mExpandableListData.get(mExpandableListTitle.get(groupPosition)))).get(childPosition).toString();
                mActionBar.setTitle(selectedItem);
                int index = 0;
                for (Display display : mDisplays) {
                    if (display.getTitle().equals(selectedItem)) {
                        moveToDisplay(index);
                        break;
                    }
                    index ++;
                }

                mDrawerLayout.closeDrawer(GravityCompat.START);
                mExpandableListView.collapseGroup(groupPosition);
                return false;
            }
        });
    }

    private void setDrawerListViewWidth() {
        int width = getResources().getDisplayMetrics().widthPixels/2;
        DrawerLayout.LayoutParams params = (android.support.v4.widget.DrawerLayout.LayoutParams) mExpandableListView.getLayoutParams();
        params.width = width;
        mExpandableListView.setLayoutParams(params);
    }

    private void setupNavigationDrawer() {
        updateHiddenDisplayNumberSet();
        mDrawerLayout = (DrawerLayout) getActivity().findViewById(R.id.drawer_layout);
        mExpandableListView = (ExpandableListView) getActivity().findViewById(R.id.navigation);
        setDrawerListViewWidth();
        mExpandableListData = getListData();
        mExpandableListTitle = new ArrayList(mExpandableListData.keySet());

        setDrawerItems();
        mDrawerToggle = new ActionBarDrawerToggle(getActivity(), mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                updateHiddenDisplayNumberSet();
                getActivity().invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                getActivity().invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };

        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mNavDrawerSet = true;
        ActionBar supportActionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        supportActionBar.setDisplayHomeAsUpEnabled(true);
        supportActionBar.setHomeButtonEnabled(true);
    }

    private void updateHiddenDisplayNumberSet() {
        HashSet<Integer> mHiddenDisplayNumberSet = new HashSet<>();
        for (Map.Entry<Display, List<Question>> curEntry : mDisplayQuestions.entrySet()) {
            boolean isSkip = true;
            for (Question curQuestion : curEntry.getValue()) {
                if (!mQuestionsToSkipSet.contains(curQuestion.getQuestionIdentifier())) {
                    isSkip = false;
                    break;
                }
            }
            if (isSkip) {
                mHiddenDisplayNumberSet.add(mDisplays.indexOf(curEntry.getKey()));
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_survey, menu);
        if (!mNavDrawerSet) {
            setupNavigationDrawer();
        }
        setSelectedDrawerItemChecked();
        setLanguageSelection(menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_item_previous).setEnabled(mDisplayNumber != 0 ||
                !mPreviousDisplays.isEmpty());
        menu.findItem(R.id.menu_item_next).setVisible(mDisplayNumber != mDisplays.size() - 1)
                .setEnabled(true);
        menu.findItem(R.id.menu_item_finish).setVisible(mDisplayNumber == mDisplays.size() - 1)
                .setEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {
            case R.id.menu_item_previous:
                moveToPreviousDisplay();
                return true;
            case R.id.menu_item_next:
                moveToNextDisplay();
                return true;
            case R.id.menu_item_finish:
                finishSurvey();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setLanguageSelection(Menu menu) {
        MenuItem item = menu.findItem(R.id.language_spinner);
        Spinner spinner = (Spinner) item.getActionView();
        final List<String> languageCodes = Instrument.getLanguages();
        ArrayList<String> displayLanguages = new ArrayList<>();
        for (String languageCode: languageCodes) {
            displayLanguages.add(new Locale(languageCode).getDisplayLanguage());
        }
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, displayLanguages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != languageCodes.indexOf(AppUtil.getAdminSettingsInstance().getLanguage())) {
                    AppUtil.getAdminSettingsInstance().setLanguage(languageCodes.get(position));
                    LocaleManager.setNewLocale(getActivity(), languageCodes.get(position));
                    recreateActivity();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        spinner.setSelection(languageCodes.indexOf(AppUtil.getAdminSettingsInstance().getLanguage()));
    }

    private void recreateActivity() {
        Intent i = new Intent(getActivity(), SurveyActivity.class);
        i.putExtra(SurveyFragment.EXTRA_INSTRUMENT_ID, mSurvey.getInstrument().getRemoteId());
        i.putExtra(SurveyFragment.EXTRA_SURVEY_ID, mSurvey.getId());
        i.putExtra(SurveyFragment.EXTRA_QUESTION_NUMBER, mSurvey.getLastQuestion().getNumberInInstrument() - 1);
        i.putExtra(SurveyFragment.EXTRA_AUTHORIZE_SURVEY, false);
//        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startActivity(i, ActivityOptions.makeSceneTransitionAnimation(getActivity()).toBundle());
        } else {
            startActivity(i);
        }
    }

    private void moveToPreviousDisplay() {
        showIndeterminateProgressBar();
        if (mDisplayNumber >= 0 && mDisplayNumber < mDisplays.size() && mPreviousDisplays.size()
                > 0) {
            mDisplayNumber = mPreviousDisplays.remove(mPreviousDisplays.size() - 1);
            mDisplay = mDisplays.get(mDisplayNumber);
        } else {
            mDisplayNumber -= 1;
            mDisplay = mDisplays.get(mDisplayNumber);
        }
        refreshUIComponents();
    }

    private void showIndeterminateProgressBar() {
        mDrawerLayout.closeDrawer(mExpandableListView);
        mIndeterminateProgressBar.setVisibility(View.VISIBLE);
    }

    protected void hideIndeterminateProgressBar() {
        mIndeterminateProgressBar.setVisibility(View.GONE);
    }

    private void refreshUIComponents() {
        if (!isAdded()) return;
        hideSoftInputWindow();
        createQuestionFragments();
        hideQuestionsInDisplay();
        updateDisplayLabels();
        setParticipantLabel();
    }

    private void hideSoftInputWindow() {
        SurveyActivity activity = (SurveyActivity) getActivity();
        if (activity != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) activity
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null && activity.getCurrentFocus() != null) {
                inputMethodManager.hideSoftInputFromWindow(
                        activity.getCurrentFocus().getWindowToken(), 0);
            }
        }
    }

    private void moveToNextDisplay() {
        showIndeterminateProgressBar();
        mPreviousDisplays.add(mDisplayNumber);
        for (int i = mDisplayNumber + 1; i < mDisplays.size(); i++) {
            boolean skipDisplay = true;
            for (Question curQuestion : mDisplayQuestions.get(mDisplays.get(i))) {
                if (!mQuestionsToSkipSet.contains(curQuestion.getQuestionIdentifier())) {
                    skipDisplay = false;
                    break;
                }
            }
            if (!skipDisplay) {
                mDisplayNumber = i;
                mDisplay = mDisplays.get(mDisplayNumber);
                break;
            } else if (i == mDisplays.size() - 1) {
                goToReviewPage();
            }
        }
        refreshUIComponents();
    }

    private void moveToDisplay(int position) {
        showIndeterminateProgressBar();
        if (mDisplayNumber != position) {
            mPreviousDisplays.add(mDisplayNumber);
            mDisplayNumber = position;
            mDisplay = mDisplays.get(mDisplayNumber);
            refreshUIComponents();
        } else {
            hideIndeterminateProgressBar();
        }
    }

    private void updateQuestionsToSkipMap(String questionIdentifier, List<Question> questionsToSkip) {
        if (questionsToSkip == null || questionsToSkip.size() == 0) {
            if (mQuestionsToSkipMap.containsKey(questionIdentifier)) {
                mQuestionsToSkipMap.remove(questionIdentifier);
            }
        } else {
            mQuestionsToSkipMap.put(questionIdentifier, questionsToSkip);
        }
    }

    private void updateQuestionsToSkipSet() {
        mQuestionsToSkipSet = new HashSet<>();
        for (HashMap.Entry<String, List<Question>> curPair : mQuestionsToSkipMap.entrySet()) {
            for (Question q: curPair.getValue()) {
                if (q != null) mQuestionsToSkipSet.add(q.getQuestionIdentifier());
            }
        }
    }

    private void unSetSkipQuestionResponse() {
        for (String curSkip : mQuestionsToSkipSet) {
            if (curSkip != null) {
                Response curResponse = mResponses.get(Question.findByQuestionIdentifier(curSkip));
                if (curResponse != null) {
                    curResponse.setResponse("");
                    curResponse.setSpecialResponse("");
                    curResponse.setOtherResponse("");
                    curResponse.setDeviceUser(AuthUtils.getCurrentUser());
                    curResponse.save();
                }
            }
        }
    }

    private void hideQuestionsInDisplay() {
        updateQuestionsToSkipSet();
        if (!mDisplay.getMode().equals(Display.DisplayMode.TABLE.toString())) {
            FragmentManager fm = getChildFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            HashSet<Integer> hideSet = new HashSet<>();
            List<String> ids = new ArrayList<>();
            for (Question q : mDisplay.questions()) {
                ids.add(q.getQuestionIdentifier());
            }
            for (String curSkip : mQuestionsToSkipSet) {
                int index = ids.indexOf(curSkip);
                if (index != -1) {
                    hideSet.add(index);
                    ft.hide(mQuestionFragments.get(index));
                }
            }
            for (int i = 0; i < mQuestionFragments.size(); i++) {
                if (!hideSet.contains(i)) {
                    ft.show(mQuestionFragments.get(i));
                }
            }
            ft.commit();
        }
    }

    protected void setIntegerLoopQuestions(Question question, String response) {
        List<LoopQuestion> loopQuestions = question.loopQuestions();
        List<Question> questionsToHide = new ArrayList<>();
        for (LoopQuestion lq : loopQuestions) {
            questionsToHide.add(lq.loopedQuestion());
        }
        int start = 1;
        if (!TextUtils.isEmpty(response)) {
            start = Integer.parseInt(response);
        }
        for (int k = start + 1; k <= Instrument.LOOP_MAX; k++) {
            for (LoopQuestion lq : loopQuestions) {
                String id = question.getQuestionIdentifier() + "_" + lq.loopedQuestion().getQuestionIdentifier()
                        + "_" + k;
                questionsToHide.add(Question.findByQuestionIdentifier(id));
            }
        }
        mQuestionsToSkipMap.put(question.getQuestionIdentifier(), questionsToHide);
        hideQuestionsInDisplay();
    }

    protected void setMultipleResponseLoopQuestions(Question question, String text) {
        List<String> responses;
        if (question.hasListResponses()) {
            responses = Arrays.asList(text.split(Response.LIST_DELIMITER, -1)); // Keep empty values
        } else {
            responses = Arrays.asList(text.split(Response.LIST_DELIMITER)); // Ignore empty values
        }
        List<LoopQuestion> loopQuestions = question.loopQuestions();
        List<Question> questionsToHide = new ArrayList<>();
        for (LoopQuestion lq : loopQuestions) {
            questionsToHide.add(lq.loopedQuestion());
        }
        int optionsSize = question.defaultOptions().size() - 1;
        if (question.isOtherQuestionType()) {
            optionsSize += 1;
        }
        for (int k = 0; k <= optionsSize; k++) {
            for (LoopQuestion lq : loopQuestions) {
                if (question.hasMultipleResponses()) {
                    if (!responses.contains(String.valueOf(k))) {
                        String id = question.getQuestionIdentifier() + "_" + lq.loopedQuestion().getQuestionIdentifier()
                                + "_" + k;
                        questionsToHide.add(Question.findByQuestionIdentifier(id));
                    }
                } else if (question.hasListResponses()) {
                    if (TextUtils.isEmpty(text) || TextUtils.isEmpty(responses.get(k))) {
                        String id = question.getQuestionIdentifier() + "_" + lq.loopedQuestion().getQuestionIdentifier()
                                + "_" + k;
                        questionsToHide.add(Question.findByQuestionIdentifier(id));
                    }
                }
            }
        }
        mQuestionsToSkipMap.put(question.getQuestionIdentifier(), questionsToHide);
        hideQuestionsInDisplay();
    }

    protected void setNextQuestion(String currentQuestionIdentifier, String nextQuestionIdentifier,
                                   String questionIdentifier) {
        List<Question> skipList = new ArrayList<>();
        boolean toBeSkipped = false;
        for (Question curQuestion : getQuestions(mDisplay)) {
            if (curQuestion.getQuestionIdentifier().equals(nextQuestionIdentifier)) break;
            if (toBeSkipped) skipList.add(curQuestion);
            if (curQuestion.getQuestionIdentifier().equals(currentQuestionIdentifier)) toBeSkipped = true;
        }
        updateQuestionsToSkipMap(questionIdentifier + "/skipTo", skipList);
        hideQuestionsInDisplay();
    }

    protected void startSurveyCompletion(Question question) {
        List<Question> displayQuestions = getQuestions(mDisplay);
        List<Question> skipList = new ArrayList<>(displayQuestions.subList(
                displayQuestions.indexOf(question) + 1, displayQuestions.size()));
        updateQuestionsToSkipMap(question.getQuestionIdentifier() + "/skipTo", skipList);
        hideQuestionsInDisplay();
    }

    protected void reAnimateFollowUpFragment(Question currentQuestion) {
        FragmentManager fm = getChildFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        for (FollowUpQuestion question : currentQuestion.toFollowUpOnQuestions()) {
            int index = mDisplay.questions().indexOf(question.getFollowUpQuestion());
            if (index > -1 && index <= mDisplay.questions().size() - 1) {
                QuestionFragment qf = mQuestionFragments.get(index);
                ft.detach(qf);
                ft.attach(qf);
                ft.commit();
            }
        }
    }

    protected void setMultipleSkipQuestions(Option selectedOption, Question currentQuestion) {
        List<Question> skipList = new ArrayList<>();
        if (selectedOption != null) {
            for (MultipleSkip questionToSkip : currentQuestion.optionMultipleSkips(selectedOption)) {
                skipList.add(questionToSkip.getSkipQuestion());
            }
        }
        updateQuestionsToSkipMap(currentQuestion.getQuestionIdentifier() + "/multi", skipList);
        hideQuestionsInDisplay();
    }

    protected void setMultipleSkipQuestions2(List<Option> options, Question currentQuestion) {
        HashSet<Question> skipSet = new HashSet<>();
        for (Option option : options) {
            for (MultipleSkip skip : currentQuestion.optionMultipleSkips(option)) {
                skipSet.add(skip.getSkipQuestion());
            }
        }
        updateQuestionsToSkipMap(currentQuestion.getQuestionIdentifier() + "/multi",
                new ArrayList<>(skipSet));
        hideQuestionsInDisplay();
    }

    private boolean checkRules() {
        return new RuleBuilder(getActivity())
                .addRule(new InstrumentSurveyLimitRule(mInstrument,
                        getActivity().getString(R.string.rule_failure_instrument_survey_limit)))
                .addRule(new InstrumentTimingRule(mInstrument, getResources().getConfiguration()
                        .locale,
                        getActivity().getString(R.string.rule_failure_survey_timing)))
                .addRule(new InstrumentSurveyLimitPerMinuteRule(mInstrument,
                        getActivity().getString(R.string.rule_instrument_survey_limit_per_minute)))
                .showToastOnFailure(true)
                .checkRules()
                .getResult();
    }

    private void launchRosterSurvey() {
        if (mInstrument.isRoster()) {
            Intent i = new Intent(getActivity(), RosterActivity.class);
            i.putExtra(RosterActivity.EXTRA_INSTRUMENT_ID, mInstrument.getRemoteId());
            i.putExtra(RosterActivity.EXTRA_PARTICIPANT_METADATA, mMetadata);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getActivity().startActivity(i, ActivityOptions.makeSceneTransitionAnimation
                        (getActivity
                                ()).toBundle());
                getActivity().finishAfterTransition();
            } else {
                getActivity().startActivity(i);
                getActivity().finish();
            }
        }
    }

    public void loadOrCreateSurvey() {
        Long surveyId = getActivity().getIntent().getLongExtra(EXTRA_SURVEY_ID, -1);
        if (surveyId == -1) {
            mSurvey = new Survey();
            mSurvey.setInstrumentRemoteId(mInstrument.getRemoteId());
            mSurvey.setMetadata(mMetadata);
            mSurvey.setProjectId(mInstrument.getProjectId());
            mSurvey.setLanguage(AppUtil.getDeviceLanguage());
            mSurvey.save();
        } else {
            mSurvey = Model.load(Survey.class, surveyId);
        }
    }

    private void setSelectedDrawerItemChecked() {
//        if (mNavigationView != null) {
//            int index = mDisplayNumber;
//            for (int num : mHiddenDisplayNumberSet) {
//                if (num < mDisplayNumber) {
//                    index--;
//                }
//            }
////            for (int i = 0; i < mDisplays.size() - mHiddenDisplayNumberSet.size(); i++) {
////                mNavigationView.getMenu().getItem(i).setChecked(false);
////            }
//            for (int i = 0; i < mDisplayTitles.size() - mHiddenDisplayNumberSet.size(); i++) {
//                mNavigationView.getMenu().getItem(i).setChecked(false);
//            }
//            if (index > -1 && index < mNavigationView.getMenu().size()) {
//                mNavigationView.getMenu().getItem(index).setChecked(true);
//            }
//        }
    }

    protected void createQuestionFragments() {
        if (!isActivityFinished) {
            // Hide previous fragments
            FragmentTransaction fragmentTransaction = getChildFragmentManager().beginTransaction();
            fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            for (Fragment fragment : mQuestionFragments) {
                fragmentTransaction.hide(fragment);
            }
            fragmentTransaction.commitNow();

            // Add/show new fragments
            fragmentTransaction = getChildFragmentManager().beginTransaction();
            fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            mQuestionFragments.clear();
            setSelectedDrawerItemChecked();
            for (Question question : getQuestions(mDisplay)) {
                int frameLayoutId;
                String qfTag;
                if (isEmpty(question.getTableIdentifier())) {
                    // Add large offset to avoid id conflicts
                    frameLayoutId = new BigDecimal(question.getRemoteId()).intValueExact() + OFFSET;
                    qfTag = mSurvey.getId().toString() + "-" + question.getId().toString();
                } else {
                    long sumId = 0;
                    for (Question q: mDisplay.tableQuestions(question.getTableIdentifier())) {
                        sumId += q.getRemoteId();
                    }
                    frameLayoutId = new BigDecimal(sumId).intValueExact() + OFFSET;
                    qfTag = mSurvey.getId().toString() + "-" + question.getTableIdentifier();
                }

                FrameLayout frameLayout = (FrameLayout) getActivity().findViewById(frameLayoutId);
                if (frameLayout == null) {
                    frameLayout = new FrameLayout(getContext());
                    frameLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup
                            .LayoutParams.MATCH_PARENT, ViewPager.LayoutParams.WRAP_CONTENT));
                    frameLayout.setId(frameLayoutId);
                    mQuestionViewLayout.addView(frameLayout);
                }

                QuestionFragment questionFragment = (QuestionFragment) getChildFragmentManager()
                        .findFragmentByTag(qfTag);
                if (questionFragment == null || isScreenRotated) {
                    if (isEmpty(question.getTableIdentifier())) {
                        Bundle bundle = new Bundle();
                        bundle.putString("QuestionIdentifier", question.getQuestionIdentifier());

                        questionFragment = (QuestionFragment) QuestionFragmentFactory
                                .createQuestionFragment(question);
                        questionFragment.setArguments(bundle);
                        fragmentTransaction.add(frameLayout.getId(), questionFragment, qfTag);
                    } else {
                        if (question.getQuestionType() == Question.QuestionType.SELECT_ONE) {
                            questionFragment = new SingleSelectMultipleQuestionsFragment();
                        } else if (question.getQuestionType() == Question.QuestionType
                                .SELECT_MULTIPLE) {
                            questionFragment = new MultipleSelectMultipleQuestionsFragment();
                        }
                        Bundle bundle = new Bundle();
                        ArrayList<String> questionsToSkip = new ArrayList<>();
                        for (String curSkip : mQuestionsToSkipSet) {
                            if (curSkip != null)
                                questionsToSkip.add(curSkip);
                        }
                        bundle.putStringArrayList(MultipleQuestionsFragment
                                .EXTRA_SKIPPED_QUESTION_ID_LIST, questionsToSkip);
                        bundle.putLong(MultipleQuestionsFragment.EXTRA_DISPLAY_ID, mDisplay
                                .getRemoteId());
                        bundle.putLong(MultipleQuestionsFragment.EXTRA_SURVEY_ID, mSurvey.getId());
                        bundle.putString(MultipleQuestionsFragment.EXTRA_TABLE_ID, question
                                .getTableIdentifier());
                        questionFragment.setArguments(bundle);
                        fragmentTransaction.add(frameLayout.getId(), questionFragment, qfTag);
                    }
                } else {
                    fragmentTransaction.show(questionFragment);
                }
                mQuestionFragments.add(questionFragment);
            }
            fragmentTransaction.commit();
        }
    }

    public Display getDisplay() {
        return mDisplay;
    }

    public Question getQuestion(String identifier) {
        for (Question question : mDisplayQuestions.get(mDisplay)) {
            if (question.getQuestionIdentifier().equals(identifier)) {
                return question;
            }
        }
        return null;
    }

    public HashSet<String> getQuestionsToSkipSet() {
        return mQuestionsToSkipSet;
    }

    protected NestedScrollView getScrollView() {
        return mScrollView;
    }

    protected List<Question> getQuestions(Display display) {
        return mDisplayQuestions.get(display);
    }

    public Survey getSurvey() {
        return mSurvey;
    }

    public HashMap<Question, Response> getResponses() {
        return mResponses;
    }

    public HashMap<Question, List<Option>> getOptions() {
        return mOptions;
    }

    public HashMap<Long, List<Option>> getSpecialOptions() {
        return mSpecialOptions;
    }

    public List<DisplayInstruction> getDisplayInstructions(Display display) {
        return mDisplayInstructions.get(display);
    }

    /*
     * Destroy this activity, and save the survey and mark it as
     * complete.  Send to server if network is available.
     */
    public void finishSurvey() {
        unSetSkipQuestionResponse();
        if (AppUtil.getAdminSettingsInstance().getRecordSurveyLocation()) {
            setSurveyLocation();
        }
        if (mSurvey.emptyResponses().size() > 0) {
            goToReviewPage();
        } else {
            checkForCriticalResponses();
        }
    }

    private void checkForCriticalResponses() {
        final List<String> criticalResponses = getCriticalResponses();
        if (criticalResponses.size() > 0) {
            String[] criticalQuestions = new String[criticalResponses.size()];
            for (int k = 0; k < criticalResponses.size(); k++) {
                criticalQuestions[k] = Question.findByQuestionIdentifier(criticalResponses.get(k)
                ).getNumberInInstrument()
                        + ": " + criticalResponses.get(k);
            }

            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            View content = LayoutInflater.from(getActivity()).inflate(R.layout
                    .critical_responses_dialog, null);
            ListView listView = (ListView) content.findViewById(R.id.critical_list);
            listView.setAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout
                    .simple_selectable_list_item, criticalQuestions));

            builder.setTitle(R.string.critical_message_title)
                    .setMessage(mInstrument.getCriticalMessage())
                    .setView(content)
                    .setPositiveButton(R.string.submit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int button) {
                            mSurvey.setCriticalResponses(true);
                            scoreAndCompleteSurvey();
                        }
                    })
                    .setNegativeButton(R.string.review, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            refreshView();
                        }
                    });
            final AlertDialog criticalDialog = builder.create();
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    refreshView();
                    criticalDialog.dismiss();
                }
            });
            criticalDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    criticalDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                            .setBackgroundColor(getResources().getColor(R.color.green));
                    criticalDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            .setBackgroundColor(getResources().getColor(R.color.red));
                }
            });
            criticalDialog.show();
        } else {
            mSurvey.setCriticalResponses(false);
            scoreAndCompleteSurvey();
        }
    }

    private void scoreAndCompleteSurvey() {
        isActivityFinished = true;
        if (mInstrument.isScorable()) {
            new ScoreSurveyTask().execute(mSurvey);
        } else {
            mSurvey.setAsComplete(true);
            mSurvey.save();
            finishActivity();
        }
    }

    private List<String> getCriticalResponses() {
        List<String> criticalQuestions = new ArrayList<String>();
        if (mInstrument.criticalQuestions().size() > 0) {
            for (Question question : mInstrument.criticalQuestions()) {
                Response response = mResponses.get(question);
                Set<String> optionSet = new HashSet<String>();
                Set<String> responseSet = new HashSet<String>();
                if (response != null) {
                    for (Option option : question.criticalOptions()) {
                        optionSet.add(Integer.toString(question.defaultOptions().indexOf(option)));
                    }
                    if (!isEmpty(response.getText())) {
                        responseSet.addAll(Arrays.asList(response.getText().split(",")));
                    }
                    optionSet.retainAll(responseSet);
                }
                if (optionSet.size() > 0) {
                    criticalQuestions.add(question.getQuestionIdentifier());
                }
            }
        }
        return criticalQuestions;
    }

    private void goToReviewPage() {
        ArrayList<String> questionsToSkip = new ArrayList<>();
        for (String curSkip : mQuestionsToSkipSet) {
            if (curSkip != null) questionsToSkip.add(curSkip);
        }
        Intent i = new Intent(getActivity(), ReviewPageActivity.class);
        Bundle b = new Bundle();
        b.putLong(ReviewPageFragment.EXTRA_REVIEW_SURVEY_ID, mSurvey.getId());
        b.putStringArrayList(ReviewPageFragment.EXTRA_SKIPPED_QUESTION_ID_LIST, questionsToSkip);
        i.putExtras(b);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startActivityForResult(i, REVIEW_CODE, ActivityOptions.makeSceneTransitionAnimation
                    (getActivity
                            ()).toBundle());
        } else {
            startActivityForResult(i, REVIEW_CODE);
        }
    }

    private void setSurveyLocation() {
        if (mLocationManager == null) {
            startLocationUpdates();
        } else {
            mSurvey.setLatitude(mLocationManager.getLatitude());
            mSurvey.setLongitude(mLocationManager.getLongitude());
        }
    }

    private void setParticipantLabel() {
        String surveyMetaData = mSurvey.getMetadata();
        if (!isEmpty(surveyMetaData)) {
            try {
                JSONObject metadata = new JSONObject(surveyMetaData);
                if (metadata.has("survey_label")) {
                    mParticipantLabel.setText(metadata.getString("survey_label"));
                }
            } catch (JSONException er) {
                Log.e(TAG, er.getMessage());
            }
        }
    }

    private void updateDisplayLabels() {
        if (mDisplay != null && mDisplay.questions().size() > 0) {
            // Screen title
            if (!mDisplay.getMode().equals(Display.DisplayMode.SINGLE.toString())) {
                mDisplayTitle.setText(String.format(Locale.getDefault(), "%s %s%d %s %d%s",
                        mDisplay.getTitle(), "(", mDisplay.questions().get(0)
                                .getNumberInInstrument(), "-", mDisplay.questions().get(mDisplay
                                .questions().size() - 1).getNumberInInstrument(), ")"));
            } else {
                mDisplayTitle.setText(mDisplay.getTitle());
            }
            // Progress text
            mDisplayIndexLabel.setText(String.format(Locale.getDefault(), "%s %d %s %d %s%d %s" +
                    " %d%s", getString(R.string.screen), mDisplayNumber + 1, getString(R.string
                    .of), mDisplays.size(), "(", mDisplay
                    .questions().get(0).getNumberInInstrument(), "-", mDisplay.questions().get
                    (mDisplay.questions().size() - 1).getNumberInInstrument(), ")"));
            // Progress bar
            mProgressBar.setProgress((int) (100 * (mDisplayNumber + 1) / (float) mDisplays.size()));
        }
    }

    private class ScoreSurveyTask extends AsyncTask<Survey, Void, Survey> {
        @Override
        protected Survey doInBackground(Survey... params) {
            Survey survey = params[0];
            for (ScoreScheme scheme : survey.getInstrument().scoreSchemes()) {
                Score score = Score.findBySurveyAndScheme(survey, scheme);
                if (score == null) {
                    score = new Score();
                    score.setSurvey(survey);
                    score.setScoreScheme(scheme);
                    score.setSurveyIdentifier(survey.identifier(AppUtil.getContext()));
                    score.save();
                }
                score.score();
            }
            return survey;
        }

        @Override
        protected void onPostExecute(Survey survey) {
            survey.setAsComplete(true);
            survey.save();
            finishActivity();
        }
    }

    private class InstrumentDataTask extends AsyncTask<Object, Void, InstrumentDataWrapper> {
        @Override
        protected InstrumentDataWrapper doInBackground(Object... params) {
            InstrumentDataWrapper instrumentData = new InstrumentDataWrapper();
            instrumentData.displayQuestions = ((Instrument) params[0]).displayQuestions();
            instrumentData.responses = ((Survey) params[1]).responsesMap();
            instrumentData.options = ((Instrument) params[0]).optionsMap();
            instrumentData.specialOptions = ((Instrument) params[0]).specialOptionsMap();
            instrumentData.displayInstructions = ((Instrument) params[0]).displayInstructions();
            return instrumentData;
        }

        @Override
        protected void onPostExecute(InstrumentDataWrapper instrumentData) {
            mDisplayQuestions = instrumentData.displayQuestions;
            mResponses = instrumentData.responses;
            mOptions = instrumentData.options;
            mSpecialOptions = instrumentData.specialOptions;
            mDisplayInstructions = instrumentData.displayInstructions;
            refreshView();
        }
    }

    private class InstrumentDataWrapper {
        public HashMap<Question, Response> responses;
        public HashMap<Question, List<Option>> options;
        public HashMap<Display, List<Question>> displayQuestions;
        public HashMap<Long, List<Option>> specialOptions;
        public HashMap<Display, List<DisplayInstruction>> displayInstructions;
    }

    private class DisplayTitlesListAdapter extends BaseExpandableListAdapter {

        private Context mContext;
        private List<String> mExpandableListTitle;
        private Map<String, List<String>> mExpandableListDetail;
        private LayoutInflater mLayoutInflater;

        DisplayTitlesListAdapter(Context context, List<String> expandableListTitle,
                                 Map<String, List<String>> expandableListDetail) {
            mContext = context;
            mExpandableListTitle = expandableListTitle;
            mExpandableListDetail = expandableListDetail;
            mLayoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public Object getChild(int listPosition, int expandedListPosition) {
            return mExpandableListDetail.get(mExpandableListTitle.get(listPosition)).get(expandedListPosition);
        }

        @Override
        public long getChildId(int listPosition, int expandedListPosition) {
            return expandedListPosition;
        }

        @Override
        public View getChildView(int listPosition, final int expandedListPosition,
                                 boolean isLastChild, View convertView, ViewGroup parent) {
            final String expandedListText = (String) getChild(listPosition, expandedListPosition);
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.list_item_text_view, null);
            }
            TextView expandedListTextView = (TextView) convertView.findViewById(R.id.expandedListItem);
            expandedListTextView.setText(expandedListText);
            return convertView;
        }

        @Override
        public int getChildrenCount(int listPosition) {
            return mExpandableListDetail.get(mExpandableListTitle.get(listPosition)).size();
        }

        @Override
        public Object getGroup(int listPosition) {
            return mExpandableListTitle.get(listPosition);
        }

        @Override
        public int getGroupCount() {
            return mExpandableListTitle.size();
        }

        @Override
        public long getGroupId(int listPosition) {
            return listPosition;
        }

        @Override
        public View getGroupView(int listPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            String listTitle = (String) getGroup(listPosition);
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.list_group, null);
            }
            TextView listTitleTextView = (TextView) convertView.findViewById(R.id.listTitle);
            listTitleTextView.setTypeface(mInstrument.getTypeFace(mContext), Typeface.BOLD);
            listTitleTextView.setText(listTitle);
            return convertView;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int listPosition, int expandedListPosition) {
            return true;
        }
    }
}