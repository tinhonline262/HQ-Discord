package HQBot;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class Question {
	
	private String questionText;
	private String[] answers = new String[3];
	private Long[] picked = new Long[3];
	private int correct;
	private Long id;
	
	public Question(JSONObject data) {
		setQuestionText((String) data.get("question"));
		setAnswers((JSONArray) data.get("answers"));
		setID((Long) data.get("questionId"));
	}
	
	private void setQuestionText(String s) {
		questionText = s;
	}
	
	private void setAnswers(JSONArray array) {
		for(int i=0; i<3; i++) {
			answers[i] = (String)((JSONObject) array.get(i)).get("text");
		}
	}
	
	private void setID(Long id) {
		this.id = id;
	}
	
	public void questionSummary(JSONArray array) {
		for(int i=0; i<3; i++) {
			picked[i] = (Long) ((JSONObject) array.get(i)).get("count");
			if((boolean) ((JSONObject) array.get(i)).get("correct")) correct = i;
		}
	}
	
	public String getQuestion() {
		return questionText;
	}
	
	public String getAnswer(int i) {
		return answers[i];
	}
	
	public Long answerCount(int i) {
		return picked[i];
	}
	
	public Long getID() {
		return id;
	}
	
	public int getCorrectIndex() {
		return correct;
	}

	public Long getCount(int i) {
		return picked[i];
	}

}
