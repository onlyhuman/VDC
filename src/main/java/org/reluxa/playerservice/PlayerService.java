package org.reluxa.playerservice;
import java.util.Collection;

import javax.enterprise.event.Observes;

import org.reluxa.AbstractService;
import org.reluxa.DuplicateUserException;
import org.reluxa.model.Player;

import com.db4o.ObjectSet;


public class PlayerService extends AbstractService {
	
	public Collection<Player> getAllPlayers() {
		ObjectSet<Player> players =  db.query(Player.class);
		return players.subList(0, players.size());
	}
	
	public void createUser(Player user) throws DuplicateUserException {
		if (hasNoDuplicates(user)) {
			db.store(user);
		} else {
			throw new DuplicateUserException();
		}
	}

	public boolean hasNoDuplicates(Player user) {
		Player temp = new Player();
		temp.setEmail(user.getEmail());
		return db.queryByExample(temp).size() == 0;
	}
	
	public void deletePlayer(@Observes DeletePlayerEvent deletePlayerEvent) {
		ObjectSet<Player> players = db.queryByExample(deletePlayerEvent.getPlayer());
		if (players.size() != 1) {
			throw new RuntimeException("Invalid object for delete:"+deletePlayerEvent.getPlayer());
		}
		db.delete(players.get(0));
	}

	public void updateUser(Player bean) {
		db.ext().bind(bean, bean.getId());
		db.store(bean);
  }
	
	
}