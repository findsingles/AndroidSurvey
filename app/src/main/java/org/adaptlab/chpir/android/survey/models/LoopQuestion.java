package org.adaptlab.chpir.android.survey.models;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;

@Table(name = "LoopQuestions")
public class LoopQuestion extends Model {
    private static final String TAG = "LoopQuestion";

    @Column(name = "Question")
    private Question mQuestion;
    @Column(name = "Parent")
    private String mParent;
    @Column(name = "RemoteId", unique = true, onUniqueConflict = Column.ConflictAction.REPLACE)
    private Long mRemoteId;
    @Column(name = "Looped")
    private String mLooped;
    @Column(name = "Deleted")
    private boolean mDeleted;

    public LoopQuestion() {
        super();
    }

    public static LoopQuestion findByRemoteId(Long id) {
        return new Select().from(LoopQuestion.class).where("RemoteId = ?", id)
                .executeSingle();
    }

    public Question getQuestion() {
        return mQuestion;
    }

    public void setQuestion(Question question) {
        mQuestion = question;
    }

    public String getParent() {
        return mParent;
    }

    public void setParent(String text) {
        mParent = text;
    }

    public void setRemoteId(Long id) {
        mRemoteId = id;
    }

    public String getLooped() {
        return mLooped;
    }

    public void setLooped(String loop) {
        mLooped = loop;
    }

    public boolean isDeleted() {
        return mDeleted;
    }

    public void setDeleted(boolean deleted) {
        mDeleted = deleted;
    }

    public Question loopedQuestion() {
        return new Select().from(Question.class).where("QuestionIdentifier = ?",
                getLooped()).executeSingle();
    }

}