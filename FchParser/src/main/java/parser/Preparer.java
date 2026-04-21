package parser;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import core.fch.BlockFileUtils;
import data.fchData.BlockMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.EsUtils;
import writeEs.RollBacker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static constants.IndicesNames.BLOCK_MARK;


public class Preparer {

	private static final Logger log = LoggerFactory.getLogger(Preparer.class);

	// Constants
	public static final int CHECK_POINTS_NUM = 5000;
	public static final int DEFAULT_REORG_PROTECT = 30;
	public static final String REORG_PROTECT_KEY = "reorgProtect";
	public static final String MAIN = "main";
	public static final String ORPHAN = "orphan";
	public static final String FORK = "fork";

	private ChainState chainState;

	public void prepare(ElasticsearchClient esClient, String blockDir, long bestHeight) throws Exception {
		prepare(esClient, blockDir, bestHeight, null);
	}

	public void prepare(ElasticsearchClient esClient, String blockDir, long bestHeight, Map<String, Object> settingMap) throws Exception {
		if(esClient==null) {
			System.out.println("Create a Java client for ES first.");
			return;
		}

		chainState = new ChainState();

		// Apply configurable reorg depth from settings
		if(settingMap != null && settingMap.containsKey(REORG_PROTECT_KEY)) {
			Object val = settingMap.get(REORG_PROTECT_KEY);
			if(val instanceof Number) {
				int reorgProtect = ((Number) val).intValue();
				if(reorgProtect > 0) {
					chainState.setReorgProtect(reorgProtect);
					log.info("Reorg protection depth set to {}", reorgProtect);
				}
			}
		}

		initialize(esClient, blockDir, bestHeight);

		ChainParser blockParser = new ChainParser(chainState);

		int parseResult;
		while(true) {
			parseResult = blockParser.startParse(esClient);
			if(parseResult == ChainParser.WRONG) {
				return;
			}
		}
	}

	private void initialize(ElasticsearchClient esClient, String path, long bestHeight) throws Exception {

		System.out.println("Initialize...");
		chainState.setPath(path);

		if(bestHeight == -1) {
			chainState.setBestHeight(-1);
			chainState.setCurrentFile("blk00000.dat");
			chainState.setPointer(0);
			chainState.setBestHash("0000000000000000000000000000000000000000000000000000000000000000");
			// Collections are empty in a fresh ChainState

		} else {
			chainState.setBestHeight(bestHeight);
			SearchResponse<BlockMask> response = esClient.search(s->s.index(BLOCK_MARK)
							.query(q->q.term(t->t.field("height").value(bestHeight)))
					, BlockMask.class);

			List<Hit<BlockMask>> hits = response.hits().hits();
			if (hits.isEmpty()) {
				log.error("No block mark found at height {}", bestHeight);
				throw new Exception("No block mark found at height " + bestHeight);
			}
			BlockMask backToBlockMask = hits.get(0).source();

			if(backToBlockMask != null) {
				new RollBacker().rollback(esClient, backToBlockMask.getHeight());

				chainState.setBestHash(backToBlockMask.getId());
				chainState.setBestHeight(backToBlockMask.getHeight());
				chainState.setCurrentFile(BlockFileUtils.getFileNameWithOrder(backToBlockMask.get_fileOrder()));
				chainState.setPointer(backToBlockMask.get_pointer() + backToBlockMask.getSize() + 8);
			}

			// Refresh ES indices to ensure recently written data is searchable
			// (replaces the unreliable sleep(5) approach)
			esClient.indices().refresh(r -> r.index(BLOCK_MARK));

			chainState.initMainList(readMainList(esClient));
			chainState.initOrphanList(readOrphanList(esClient, bestHeight));
			chainState.initForkList(readForkList(esClient, bestHeight));
		}
	}

	public static ArrayList<BlockMask> readForkList(ElasticsearchClient esClient, long bestHeight) throws ElasticsearchException, IOException {

		System.out.println("Reading fork blockMark list...");
		int size = EsUtils.READ_MAX;
		ArrayList<BlockMask> readList = new ArrayList<>();

		SearchResponse<BlockMask> response = esClient.search(s->s.index(BLOCK_MARK)
						.query(q->q.bool(b->b
								.filter(f->f
										.term(t->t.field("status").value("fork")))
								.must(m->m
										.range(r->r.field("height").gt(JsonData.of(bestHeight-DEFAULT_REORG_PROTECT))))
						))
						.size(size)
						.sort(so->so.field(f->f
								.field("height")
								.order(SortOrder.Asc)))
				, BlockMask.class);

		List<Hit<BlockMask>> hitList = response.hits().hits();
		for (Hit<BlockMask> hit : hitList) {
			readList.add(hit.source());
		}

		// Paginate with search_after if there are more results
		while (hitList.size() >= size) {
			List<FieldValue> lastSort = hitList.get(hitList.size() - 1).sort();
			List<FieldValue> finalLastSort = lastSort;
			response = esClient.search(s->s.index(BLOCK_MARK)
							.query(q->q.bool(b->b
									.filter(f->f
											.term(t->t.field("status").value("fork")))
									.must(m->m
											.range(r->r.field("height").gt(JsonData.of(bestHeight-DEFAULT_REORG_PROTECT))))
							))
							.size(size)
							.sort(so->so.field(f->f
									.field("height")
									.order(SortOrder.Asc)))
							.searchAfter(finalLastSort)
					, BlockMask.class);

			hitList = response.hits().hits();
			for (Hit<BlockMask> hit : hitList) {
				readList.add(hit.source());
			}
		}

		log.info("Read {} fork block marks", readList.size());
		return readList;
	}

	public static ArrayList<BlockMask> readOrphanList(ElasticsearchClient esClient, long bestHeight) throws ElasticsearchException, IOException {

		System.out.println("Reading orphan blockMark list...");
		int size = EsUtils.READ_MAX;
		ArrayList<BlockMask> readList = new ArrayList<>();

		SearchResponse<BlockMask> response = esClient.search(s->s.index(BLOCK_MARK)
						.query(q->q
								.bool(b->b
										.should(s1->s1
												.term(t->t
														.field("status")
														.value(ORPHAN)))
										.should(s2->s2
												.bool(b1->b1
														.must(m->m
																.range(r->r
																		.field("height")
																		.gt(JsonData.of(bestHeight))))
														.must(m1->m1
																.range(r1->r1
																		.field("orphanHeight")
																		.lte(JsonData.of(bestHeight))))
												)
										)
								)
						)
						.size(size)
						.sort(so->so
								.field(f->f
										.field("_fileOrder").order(SortOrder.Asc)
										.field("_pointer").order(SortOrder.Asc))
						)
				, BlockMask.class);

		List<Hit<BlockMask>> hitList = response.hits().hits();
		for (Hit<BlockMask> hit : hitList) {
			readList.add(hit.source());
		}

		// Paginate with search_after if there are more results
		while (hitList.size() >= size) {
			List<FieldValue> lastSort = hitList.get(hitList.size() - 1).sort();
			List<FieldValue> finalLastSort = lastSort;
			response = esClient.search(s->s.index(BLOCK_MARK)
							.query(q->q
									.bool(b->b
											.should(s1->s1
													.term(t->t
															.field("status")
															.value(ORPHAN)))
											.should(s2->s2
													.bool(b1->b1
															.must(m->m
																	.range(r->r
																			.field("height")
																			.gt(JsonData.of(bestHeight))))
															.must(m1->m1
																	.range(r1->r1
																			.field("orphanHeight")
																			.lte(JsonData.of(bestHeight))))
													)
											)
									)
							)
							.size(size)
							.sort(so->so
									.field(f->f
											.field("_fileOrder").order(SortOrder.Asc)
											.field("_pointer").order(SortOrder.Asc))
							)
							.searchAfter(finalLastSort)
					, BlockMask.class);

			hitList = response.hits().hits();
			for (Hit<BlockMask> hit : hitList) {
				readList.add(hit.source());
			}
		}

		for(BlockMask bm : readList) {
			bm.setStatus(ORPHAN);
			bm.setHeight(0L);
		}

		log.info("Read {} orphan block marks", readList.size());
		return readList;
	}

	public static ArrayList<BlockMask> readMainList(ElasticsearchClient esClient) throws ElasticsearchException, IOException {

		System.out.println("Reading main blockMark list...");
		SearchResponse<BlockMask> response = esClient.search(s->s.index(BLOCK_MARK)
						.query(q->q
								.term(t->t
										.field("status")
										.value(MAIN)))
						.size(CHECK_POINTS_NUM)
						.sort(so->so.field(f->f
								.field("height")
								.order(SortOrder.Desc)))
				, BlockMask.class);

		List<Hit<BlockMask>> hitList = response.hits().hits();

		ArrayList<BlockMask> readList = new ArrayList<>();

		for(int i=hitList.size()-1; i>=0; i-- ) {
			readList.add(hitList.get(i).source());
		}

		log.info("Read {} main block marks", readList.size());
		return readList;
	}
}
