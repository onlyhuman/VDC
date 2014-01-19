package org.reluxa.bid.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.inject.Inject;

import lombok.Getter;
import lombok.Setter;

import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDate;
import org.reluxa.bid.Bid;
import org.reluxa.bid.BidStatus;
import org.reluxa.player.Player;
import org.reluxa.time.TimeServiceIF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Ordering;

public class BidEvaluator {
	
	protected Logger log = LoggerFactory.getLogger(this.getClass());
	
	public static Comparator<Bid> SCORE_COMPARATOR = new Comparator<Bid>() {
		@Override
    public int compare(Bid o1, Bid o2) {
			if (o1.getScore() == null) {
				return 1;
			} else if (o2.getScore() == null) {
				return -1;
			} else {
				return o1.getScore().compareTo(o2.getScore());
			}
		}
	};

	@Inject @Getter @Setter
	private BidServiceIF bidService;
	
	@Inject	@Getter	@Setter
	private TimeServiceIF timeService;

	@Getter
	private int maxEventsPerWeek = 5;

	/**
	 * @return Collection<Bid> which contains the scores associated for the bid. 
	 */
	public Collection<Bid> evaluteBidsForCurrentWeek(Date intervalBegin) {
		ArrayList<Bid> allBids = new ArrayList<>(bidService.getAllBids(intervalBegin));
		Collections.sort(allBids, new Comparator<Bid>() {
			@Override
			public int compare(Bid o1, Bid o2) {
				return o1.getCreationTime().compareTo(o2.getCreationTime());
			}
		});

		Map<Player, Integer> scoreTable = new HashMap<>();
		for (Bid bid : allBids) {
			updateScores(bid, scoreTable);
		}
		return allBids;
	}
	
	public Collection<Bid> evaluteBidsForCurrentWeek() {
		return evaluteBidsForCurrentWeek(getIntervalBeginDateForNow());
	}

	
	
	/**
	 * Weekly running job, which is called automatically; 
	 */
	public void runWeeklyEvaluation() {
		//find the firstDate;
		Date firstBidCreationTime = getFirstBidCreationTime();
		if (firstBidCreationTime == null) {
			log.info("There is no bid this week...");
			return;
		}
		
		final Date weekStart = getIntervalBeginDateForReferenceDate(firstBidCreationTime);
		
		//do the eval
		Collection<Bid> evaluatedBids = evaluteBidsForCurrentWeek(weekStart);

		//get the current week
		final Date weekEnd = timeService.getWeekEnd(firstBidCreationTime);
		Collection<Bid> thisWeekBids = Collections2.filter(evaluatedBids, new Predicate<Bid>(){
			@Override
      public boolean apply(@Nullable Bid bid) {
				return weekStart.before(bid.getCreationTime()) && weekEnd.after(bid.getCreationTime());
      }
		});
		List<Bid> sorted = new ArrayList<>(thisWeekBids);
		Collections.sort(sorted, SCORE_COMPARATOR);
		
		//set the status and ticket id.
		for (int i=0;i<sorted.size();i++) {
			if (i <= getMaxEventsPerWeek() && BidStatus.PENDING.toString().equals(sorted.get(i).getStatus())) {
				sorted.get(i).setStatus(BidStatus.WON.toString());
				sorted.get(i).setTicketCode(RandomStringUtils.random(2)+RandomStringUtils.randomNumeric(4));
			} else {
				sorted.get(i).setStatus(BidStatus.LOST.toString());
			}
		}

		//save to the database
		bidService.updateAll(sorted);
	}


	private Date getFirstBidCreationTime() {
	  Collection<Bid> bids = getBidService().getAllNotEvaluatedBids();
	  if (bids.size() > 0) {
			Ordering<Bid> ob = new Ordering<Bid>() {
				@Override
	      public int compare(@Nullable Bid left, @Nullable Bid right) {
					return left.getCreationTime().compareTo(right.getCreationTime());
	      }
			};
			Bid firstBid = ob.min(bids);
		  return firstBid.getCreationTime();
	  }
	  return null;
  }

	private void updateScores(Bid bid, Map<Player, Integer> scoreTable) {
		Player creator = bid.getCreator();
		Player partner = bid.getPartner();
		if (partner != null && !BidStatus.WAITING_FOR_APPOVAL.toString().equals(bid.getStatus())) {
			double ppoints = getAndIncrement(partner, scoreTable, 1);
			double cpoints = getAndIncrement(creator, scoreTable, 1);
			bid.setScore((ppoints + cpoints) * 0.5);
		} else if (partner == null) {
			bid.setScore(new Double(getAndIncrement(creator, scoreTable, 2)));
		} else {
			bid.setScore(null);
		}
	}

	private Integer getAndIncrement(Player player, Map<Player, Integer> scoreTable, int by) {
		Integer score = scoreTable.get(player);
		if (score == null) {
			score = by;
		} else {
			score += by;
		}
		scoreTable.put(player, score);
		return score;
	}

	public Date getIntervalBeginDateForNow() {
		return getIntervalBeginDateForReferenceDate(timeService.getCurrentTime());
	}
	
	
	public Date getIntervalBeginDateForReferenceDate(Date ref) {
		LocalDate currentLocal = new LocalDate(ref.getTime());
		LocalDate thisSunday = currentLocal.withDayOfWeek(DateTimeConstants.SUNDAY);
		LocalDate forWeeksBefore = thisSunday.minusWeeks(4);
		return forWeeksBefore.toDate();
	}

}