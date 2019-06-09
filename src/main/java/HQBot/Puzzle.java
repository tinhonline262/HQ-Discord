package HQBot;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class Puzzle {

	private String hint;
	private String[] puzzle;
	private Long id;
	private Long solved;
	private Long unsolved;
	
	public Puzzle(JSONObject data) {
		setHint((String) data.get("hint"));
		setPuzzle((JSONArray) data.get("puzzleState"));
		setID((Long) data.get("roundId"));
	}
	
	private void setHint(String s) {
		hint = s;
	}
	
	private void setPuzzle(JSONArray array) {
		puzzle = new String[array.size()];
		for(int i=0; i<puzzleSize(); i++) {
			puzzle[i] = (String) array.get(i);
		}
	}
	
	private void setID(Long id) {
		this.id = id;
	}
	
	public void updatePuzzle(JSONObject data) {
		for(int i=0; i<puzzleSize(); i++) {
			puzzle[i] = (String) ((JSONArray) data.get("answer")).get(i);
		}
		solved = (Long) data.get("correctAnswers");
		unsolved = (Long) data.get("incorrectAnswers");
	}
	
	public String getHint() {
		return hint;
	}
	
	public String getPuzzle(int i) {
		return puzzle[i];
	}
	
	public Long getID() {
		return id;
	}
	
	public Long getSolved() {
		return solved;
	}
	
	public Long getUnsolved() {
		return unsolved;
	}
	
	public String getAnswer() {
		String s = "";
		for(int i=0; i<puzzleSize(); i++) {
			s += puzzle[i] + " ";
		}
		return s;
	}
	
	public int puzzleSize() {
		return puzzle.length;
	}
}
