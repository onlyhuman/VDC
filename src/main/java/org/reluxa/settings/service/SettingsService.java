package org.reluxa.settings.service;

import java.io.IOException;

import javax.enterprise.inject.Produces;

import org.apache.commons.io.IOUtils;
import org.reluxa.AbstractService;
import org.reluxa.settings.Config;
import org.reluxa.settings.CurrentConfig;

import com.db4o.ObjectSet;

public class SettingsService extends AbstractService implements SettingsServiceIF {

  public static Config DEFAULT = createDefault();

  private static Config createDefault() {
    Config config = new Config();
    config.setNumberOfEventsPerWeek(6);
    config.setSenderEmailAddress("BLHSE Squash <noreply@squash.reluxa.org>");
    config.setPasswordResetTemplate(loadFromResource("/passwordreset.html"));
    config.setWeeklyEvaluationTemplate(loadFromResource("/weeklyevalresult.html"));
    return config;
  }

  @Produces @CurrentConfig @Override
  public Config getConfig() {
    ObjectSet<Config> os = db.query(Config.class);
    if (os.size() == 0) {
      return DEFAULT;
    } else {
      return os.get(0);
    }
  }

  @Override
  public void saveConfig(Config config) {
    db.store(config);
  }
  
  @Override
  public void resetConfig() {
  	db.delete(getConfig());
  	db.store(createDefault());
  }
  
  private static String loadFromResource(String name) {
  	String result = "N/A";
    try {
      result = IOUtils.toString(SettingsService.class.getResourceAsStream(name),"UTF-8");
    } catch (IOException e) {
      e.printStackTrace();
    }
    return result;
  }
  

}
