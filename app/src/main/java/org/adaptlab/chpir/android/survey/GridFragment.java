package org.adaptlab.chpir.android.survey;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;

import com.activeandroid.Model;

import org.adaptlab.chpir.android.survey.models.Grid;
import org.adaptlab.chpir.android.survey.models.Question;
import org.adaptlab.chpir.android.survey.models.Response;
import org.adaptlab.chpir.android.survey.models.Survey;
import org.adaptlab.chpir.android.survey.roster.listeners.ScrollViewListener;

import java.util.List;

public abstract class GridFragment extends QuestionFragment implements ScrollViewListener {
	public final static String EXTRA_GRID_ID = 
            "org.adaptlab.chpir.android.survey.grid_id";
	public final static String EXTRA_SURVEY_ID =
			"org.adaptlab.chpir.android.survey.survey_id";

	protected void createQuestionComponent(ViewGroup questionComponent){};
	
	private static final String TAG = "GridFragment";
	private Grid mGrid;
	private Survey mSurvey;
	private List<Question> mQuestions;

	@Override
    public void onCreate(Bundle savedInstanceState) {
		if (savedInstanceState != null) {
        	mGrid = Grid.findByRemoteId(savedInstanceState.getLong(EXTRA_GRID_ID));
        } else {
            mGrid = Grid.findByRemoteId(getArguments().getLong(EXTRA_GRID_ID));
        }
		mQuestions = mGrid.questions();
		init();
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Allow both portrait and landscape orientations
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
	}
	
	@Override
	public void init() {
		long surveyId = getArguments().getLong(EXTRA_SURVEY_ID);
		if (surveyId != -1) {
			mSurvey = Model.load(Survey.class, surveyId);
			createResponses();
		}
	}

	private void createResponses() {
		for (Question question : mGrid.questions()) {
			if (mSurvey.getResponseByQuestion(question) == null) {
				Response response = new Response();
				response.setQuestion(question);
				response.setSurvey(mSurvey);
				response.save();
			}
		}
	}
	
	@Override
	public void setSpecialResponse(String specialResponse) {
		for (Question question : mGrid.questions()) {
			Response response = mSurvey.getResponseByQuestion(question);
			if (response != null) {
				response.setSpecialResponse(specialResponse);
				response.setDeviceUser(AuthUtils.getCurrentUser());
				response.setResponse("");
				response.save();
				deserialize(response.getText());
				if (AppUtil.DEBUG) Log.i(TAG, "Saved special response: " + response.getSpecialResponse() + 
						" for question: " + question.getQuestionIdentifier());
			}
		}
	}
	
	@Override
	public String getSpecialResponse() {
		if (mGrid == null && mSurvey == null) {
			return "";
		}
		
		for (int k = 0; k < mGrid.questions().size(); k++) {
			Response response = mSurvey.getResponseByQuestion(mGrid.questions().get(k));
			if (response != null && !response.getSpecialResponse().equals("")) {
				return response.getSpecialResponse();
			}
		}
		
		return "";
    }

	@Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(EXTRA_GRID_ID, mGrid.getRemoteId());
        outState.putLong(EXTRA_SURVEY_ID, mSurvey.getId());
	}
	
	protected List<Question> getQuestions() {
		return mQuestions;
	}
	
	protected Grid getGrid() {
		return mGrid;
	}
	
	protected Survey getSurvey() {
		return mSurvey;
	}

}
