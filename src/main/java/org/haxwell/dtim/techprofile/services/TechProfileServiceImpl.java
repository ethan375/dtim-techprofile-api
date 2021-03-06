package org.haxwell.dtim.techprofile.services;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.haxwell.dtim.techprofile.entities.TechProfile;
import org.haxwell.dtim.techprofile.entities.TechProfileLineItem;
import org.haxwell.dtim.techprofile.entities.TechProfileTopic;
import org.haxwell.dtim.techprofile.entities.UserTechProfileLineItemScore;
import org.haxwell.dtim.techprofile.repositories.TechProfileLineItemRepository;
import org.haxwell.dtim.techprofile.repositories.TechProfileRepository;
import org.haxwell.dtim.techprofile.repositories.TechProfileTopicRepository;
import org.haxwell.dtim.techprofile.repositories.UserTechProfileLineItemScoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TechProfileServiceImpl implements TechProfileService {

	@PersistenceContext
	EntityManager em;
	
	@Autowired
	TechProfileRepository techProfileRepository;
	
	@Autowired
	TechProfileTopicRepository techProfileTopicRepository;
	
	@Autowired
	TechProfileLineItemRepository techProfileLineItemRepository;
	
	@Autowired
	UserTechProfileLineItemScoreRepository utplisRepository;
	
	@Override
	public TechProfile get(Long id) {
		Optional<TechProfile> opt = techProfileRepository.findById(id);
		
		List topicSequences = getTopicSequences(id);
		List lineItemSequences = getLineItemSequences(id);
		
		TechProfile tp = null;
		
		if (opt.isPresent()) {
			tp = opt.get();
			
			Set<TechProfileTopic> topics = tp.getTopics();
			
			Iterator<TechProfileTopic> tptIterator = topics.iterator();
			
			while (tptIterator.hasNext()) {
				TechProfileTopic topic = tptIterator.next();
				
				setSequenceOnTopic(topic, topicSequences);
				
				Set<TechProfileLineItem> lineItems = topic.getLineItems();
				
				Iterator<TechProfileLineItem> tpliIterator = lineItems.iterator();
				
				while (tpliIterator.hasNext()) {
					TechProfileLineItem tpli = tpliIterator.next();
					
					setSequenceOnLineItem(topic.getId(), tpli, lineItemSequences);
				}
			}
		}
		
		return tp;
	}
	
	@Override
	@Transactional
	public TechProfileTopic addTopic(String topicName) {
		TechProfileTopic rtn = techProfileTopicRepository.save(new TechProfileTopic(topicName));
		
		List resultList = em.createNativeQuery("SELECT max(sequence) FROM tech_profile_topic_map where tech_profile_id=1")
				.getResultList();
		
		Long currentMaxSequenceNum = 0L;
		
		if (resultList.size() > 0 && resultList.get(0) != null)
			currentMaxSequenceNum = Long.parseLong(resultList.get(0).toString());
		
		em.createNativeQuery("INSERT INTO tech_profile_topic_map (tech_profile_id, tech_profile_topic_id, sequence) VALUES (1, :topicId, :sequence)")
			.setParameter("topicId",  rtn.getId())
			.setParameter("sequence", currentMaxSequenceNum + 1)
			.executeUpdate();
		
		return rtn;
	}
	
	@Override
	@Transactional
	public TechProfileLineItem addLineItem(Long topicId, String lineItemName, String l0desc, String l1desc, String l2desc, String l3desc) {
		TechProfileLineItem rtn = techProfileLineItemRepository.save(new TechProfileLineItem(lineItemName, l0desc, l1desc, l2desc, l3desc));

		List resultList = em.createNativeQuery("SELECT max(sequence) FROM tech_profile_topic_line_item_map where tech_profile_topic_id=:topicId")
		.setParameter("topicId", topicId)
		.getResultList();
		
		Long currentMaxSequenceNum = 0L;
		
		if (resultList.size() > 0 && resultList.get(0) != null)
			currentMaxSequenceNum = Long.parseLong(resultList.get(0).toString());
		
		if (topicId > 0) {
			em.createNativeQuery("INSERT INTO tech_profile_topic_line_item_map (tech_profile_topic_id, tech_profile_line_item_id, sequence) VALUES (:topicId, :lineItemId, :sequence)")
				.setParameter("topicId", topicId)
				.setParameter("lineItemId", rtn.getId())
				.setParameter("sequence", currentMaxSequenceNum + 1)
				.executeUpdate();
		}

		return rtn;
	}
	
	@Override
	public Optional<TechProfileLineItem> getLineItem(Long lineItemId) {
		return techProfileLineItemRepository.findById(lineItemId);
	}
	
	@Override
	public TechProfileTopic updateTopic(Long topicId, String name) {
		Optional<TechProfileTopic> opt = techProfileTopicRepository.findById(topicId);
		TechProfileTopic rtn = null;
		
		if (opt.isPresent()) {
			TechProfileTopic tpt = opt.get();
			tpt.setName(name);
			
			rtn = techProfileTopicRepository.save(tpt);
		}

		return rtn;
	}
	
	@Override
	public TechProfileLineItem updateLineItem(Long lineItemId, String lineItemName, String l0desc, String l1desc, String l2desc, String l3desc) {
		Optional<TechProfileLineItem> opt = techProfileLineItemRepository.findById(lineItemId);
		TechProfileLineItem rtn = null;
		
		if (opt.isPresent()) {
			TechProfileLineItem tpli = opt.get();
			
			tpli.setL0Description(l0desc);
			tpli.setL1Description(l1desc);
			tpli.setL2Description(l2desc);
			tpli.setL3Description(l3desc);
			
			tpli.setName(lineItemName);
			
			rtn = techProfileLineItemRepository.save(tpli);
		}

		return rtn;
	}
	
	@Override
	public List<UserTechProfileLineItemScore> getUserIdScores(Long userId) {
		return utplisRepository.findByUserId(userId);
	}
	
	@Override
	@Transactional
	public boolean updateSequencesRelatedToATopicAndItsLineItems(long[] arr) {
		Optional<TechProfileTopic> opt = techProfileTopicRepository.findById(arr[1]);

		// TODO: Pass JSON to the controller, and create a POJO model, instead of the array. @RequestBody
		
		if (opt.isPresent()) {
			this.setTopicSequence(arr[1], arr[2]);
			
			if (arr[3] > 0 && arr[4] > 0)
				this.setLineItemSequence(arr[1], arr[3], arr[4]);
		}
		
		return true;
	}
	
	private void setTopicSequence(Long topicId, Long sequence) {
		em.createNativeQuery("UPDATE tech_profile_topic_map tptm SET tptm.sequence = :sequence WHERE tptm.tech_profile_topic_id = :topicId")
			.setParameter("topicId", topicId)
			.setParameter("sequence", sequence)
			.executeUpdate();
	}
	
	private void setLineItemSequence(Long topicId, Long lineItemId, Long sequence) {
		em.createNativeQuery("UPDATE tech_profile_topic_line_item_map tplitm SET tplitm.sequence=:sequence WHERE tplitm.tech_profile_topic_id=:topicId AND tplitm.tech_profile_line_item_id=:lineItemId")
		.setParameter("topicId", topicId)
		.setParameter("lineItemId", lineItemId)
		.setParameter("sequence", sequence)
		.executeUpdate();
	}
	
	/**
	 * Returns the sequence of the topics in a given tech profile.
	 * 
	 * @param techProfileId
	 * @return
	 */
	private List getTopicSequences(Long techProfileId) {
		List resultList = em.createNativeQuery("SELECT tpt.id as topic_id, tptm.sequence FROM tech_profile tp, tech_profile_topic tpt, tech_profile_topic_map tptm WHERE tp.id=:techProfileId AND tptm.tech_profile_id=tp.id AND tptm.tech_profile_topic_id=tpt.id;")
				.setParameter("techProfileId", techProfileId).getResultList();
		
		return resultList;
	}
	
	private List getLineItemSequences(Long techProfileId) {
		List resultList = em.createNativeQuery("select tpt.id as topic_id, tpli.id as tech_profile_line_item_id, tptlim.sequence FROM tech_profile tp, tech_profile_topic tpt, tech_profile_topic_map tptm, tech_profile_line_item tpli, tech_profile_topic_line_item_map tptlim WHERE tp.id=:techProfileId and tptm.tech_profile_id=tp.id and tptm.tech_profile_topic_id=tpt.id and tpt.id=tptlim.tech_profile_topic_id and tptlim.tech_profile_line_item_id=tpli.id;")
				.setParameter("techProfileId", techProfileId).getResultList();
		
		return resultList;
	}

	private TechProfileTopic setSequenceOnTopic(TechProfileTopic topic, List topicSequences) {
		int x = 0;
		TechProfileTopic rtn = null;
		
		while (rtn == null && x < topicSequences.size()) {
			Object[] ts = (Object[])topicSequences.get(x++);
			
			if (((BigInteger)ts[0]).longValue() == (Long)topic.getId()) {
				topic.setSequence(((BigInteger)ts[1]).longValue());
				rtn = topic;
			}
		}
		
		return rtn;
	}
	
	private TechProfileLineItem setSequenceOnLineItem(Long topicId, TechProfileLineItem tpli, List lineItemSequences) {
		int x = 0;
		TechProfileLineItem rtn = null;

		while (rtn == null && x < lineItemSequences.size()) {
			Object[] lis = (Object[])lineItemSequences.get(x++);

			if (((BigInteger)lis[0]).longValue() == topicId && ((BigInteger)lis[1]).longValue() == (Long)tpli.getId()) {
				tpli.setSequence(((BigInteger)lis[2]).longValue());
				rtn = tpli;
			}
		}

		return rtn;
	}
	
	/**** *** ***/
	
	public List getQuestionCountsPerCell() {

		// For all of the questions associated with each lineItemLevel
        // where a line item
        // belongs to a topic
        //  that in turn belongs to tech profile
        //  	with id = 1
		//
		//	Count all of the times any question is associated with a given lineItem and Level

		List resultList = em.createNativeQuery("select tech_profile_line_item_id, tech_profile_line_item_level_index, count(*) FROM  (select * from line_item_level_question_map lilqm where lilqm.tech_profile_line_item_id in (select tech_profile_line_item_id from tech_profile_topic_line_item_map tptlim where tptlim.tech_profile_topic_id in (select tech_profile_topic_id from tech_profile_topic_map where tech_profile_id = 1))) as tabl GROUP BY tech_profile_line_item_id, tech_profile_line_item_level_index;")
				.getResultList();
		
		return resultList;
	}

	/**** *** ***/
	public List getCorrectlyAnsweredQuestionCountsPerCell(Long userId) {	
		
		// Get the question_id of every question this user has correctly answered
		
		//	Count all of the times a question is associated with a given lineItem and Level		
		
		// TODO: When the time comes, we can limit this query to only a subset of the sessions they were in by adding "...AND SESSION_ID < ?1"
		
		List resultList = em.createNativeQuery("select tech_profile_line_item_id, tech_profile_line_item_level_index, count(*) FROM  (select * FROM line_item_level_question_map lilqm WHERE lilqm.question_id IN (SELECT DISTINCT(question_id) FROM user_question_grade WHERE user_id=:userId AND grade=2)) as tabl GROUP BY tech_profile_line_item_id, tech_profile_line_item_level_index;")
				.setParameter("userId", userId)
				.getResultList();

		return resultList;
	}

	/**** *** ***/
	public List getIncorrectlyAnsweredQuestionCountsPerCell(Long userId) {	
		
		List resultList = em.createNativeQuery("select tech_profile_line_item_id, tech_profile_line_item_level_index, count(*) FROM  (select * FROM line_item_level_question_map lilqm WHERE lilqm.question_id IN (SELECT DISTINCT(question_id) FROM user_question_grade WHERE user_id=:userId AND (grade=0 OR grade=1))) as tabl GROUP BY tech_profile_line_item_id, tech_profile_line_item_level_index;")
				.setParameter("userId", userId)
				.getResultList();

		return resultList;
	}
	/**** *** ***/
	public List getAskedQuestionCountsPerCell(Long userId) {	
		
		List resultList = em.createNativeQuery("select tech_profile_line_item_id, tech_profile_line_item_level_index, count(*) FROM  (select * FROM line_item_level_question_map lilqm WHERE lilqm.question_id IN (SELECT DISTINCT(question_id) FROM user_question_grade WHERE user_id=:userId)) as tabl GROUP BY tech_profile_line_item_id, tech_profile_line_item_level_index;")
				.setParameter("userId", userId)
				.getResultList();

		return resultList;
	}
}
