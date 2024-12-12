package SurNoRModelBased;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class ConfigEdit{
	public void updateScenario(String FPath, int sn) throws IOException, ParseException
	  {
		FileReader reader = new FileReader(FPath);
		
		JSONParser jsonParser = new JSONParser();
		JSONObject jsonObject = (JSONObject) jsonParser.parse(reader);
		
		jsonObject.put("current_scenario", sn);
		FileWriter writer = new FileWriter(FPath, false);
		writer.write(jsonObject.toString());
		writer.close();
	
	}
}
