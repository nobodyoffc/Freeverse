package parser;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import fch.BlockFileUtils;
import fch.fchData.BlockMark;
import utils.EsUtils;
import writeEs.RollBacker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static constants.IndicesNames.BLOCK_MARK;


public class Preparer {

	public static final int CHECK_POINTS_NUM = 5000;
	public static final int REORG_PROTECT = 30;
	public static final String MAIN = "main";
	public static final String ORPHAN = "orphan";
	public static final String FORK = "fork";

	public static  String Path;
	public static  String CurrentFile;
	public static  long Pointer;
	public static  String BestHash;
	public static  long BestHeight;
	public static BlockMark BestBlockMark;
	public static BlockMark BeforeBestBlockMark;
	public static ArrayList<BlockMark> orphanList;
	public static ArrayList<BlockMark> mainList;
	public static ArrayList<BlockMark> forkList;

	public void prepare(ElasticsearchClient esClient, String blockDir, long bestHeight) throws Exception {
		if(esClient==null) {
			System.out.println("Create a Java client for ES first.");
			return;
		}

		initialize(esClient, blockDir, bestHeight);

		ChainParser blockParser = new ChainParser();

		int parseResult;
		while(true) {

			parseResult = blockParser.startParse(esClient);
			if(parseResult == ChainParser.WRONG) {
				return;
			}
		}
	}
	private void initialize(ElasticsearchClient esClient, String path, long bestHeight) throws Exception {

		System.out.println("Initialize..." );
		Path = path;

		if(bestHeight == -1) {
			Path = path;
			BestHeight = -1;
			CurrentFile = "blk00000.dat";
			Pointer = 0;
			BestHash = "0000000000000000000000000000000000000000000000000000000000000000";

			Preparer.orphanList= new ArrayList<>();
			Preparer.mainList = new ArrayList<>();
			Preparer.forkList = new ArrayList<>();

		}else {
			BestHeight = bestHeight;
			SearchResponse<BlockMark> response = esClient.search(s->s.index(BLOCK_MARK)
							.query(q->q.term(t->t.field("height").value(BestHeight)))
					, BlockMark.class);

			BlockMark backToBlockMark = response.hits().hits().get(0).source();

			if(backToBlockMark!=null) {
				new RollBacker().rollback(esClient, backToBlockMark.getHeight());

				Preparer.BestHash = backToBlockMark.getId();
				Preparer.BestHeight = backToBlockMark.getHeight();
				Preparer.CurrentFile = BlockFileUtils.getFileNameWithOrder(backToBlockMark.get_fileOrder());
				Preparer.Pointer = backToBlockMark.get_pointer() + backToBlockMark.getSize() + 8;
			}
			TimeUnit.SECONDS.sleep(5);

			Preparer.mainList = readMainList(esClient);
			Preparer.orphanList = readOrphanList(esClient);
			Preparer.forkList = readForkList(esClient, BestHeight);
		}
	}

	public static ArrayList<BlockMark> readForkList(ElasticsearchClient esClient, long bestHeight) throws ElasticsearchException, IOException {

		System.out.println("Reading fork blockMark list..." );
		SearchResponse<BlockMark> response = esClient.search(s->s.index(BLOCK_MARK)
						.query(q->q.bool(b->b
								.filter(f->f
										.term(t->t.field("status").value("fork")))
								.must(m->m
										.range(r->r.field("height").gt(JsonData.of(bestHeight-REORG_PROTECT))))
						))
						.size(EsUtils.READ_MAX)
						.sort(so->so.field(f->f
								.field("height")
								.order(SortOrder.Asc)))
				, BlockMark.class);

		List<Hit<BlockMark>> hitList = response.hits().hits();

		ArrayList<BlockMark> readList = new ArrayList<>();

		for (Hit<BlockMark> hit : hitList) {
			readList.add(hit.source());
		}

		return readList;
	}
	public static ArrayList<BlockMark> readOrphanList(ElasticsearchClient esClient) throws ElasticsearchException, IOException {

		System.out.println("Reading orphan blockMark list..." );
		SearchResponse<BlockMark> response = esClient.search(s->s.index(BLOCK_MARK)
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
																		.gt(JsonData.of(Preparer.BestHeight))))
														.must(m1->m1
																.range(r1->r1
																		.field("orphanHeight")
																		.lte(JsonData.of(Preparer.BestHeight))))
												)
										)
								)

						)
						.size(EsUtils.READ_MAX)
						.sort(so->so
								.field(f->f
										.field("_fileOrder").order(SortOrder.Asc)
										.field("_pointer").order(SortOrder.Asc))
						)
				, BlockMark.class);

		List<Hit<BlockMark>> hitList = response.hits().hits();

		ArrayList<BlockMark> readList = new ArrayList<>();

		for (Hit<BlockMark> hit : hitList) {
			readList.add(hit.source());
		}

		for(BlockMark bm : readList) {
			bm.setStatus(ORPHAN);
			bm.setHeight(0L);
		}

		return readList;
	}
	public static ArrayList<BlockMark> readMainList(ElasticsearchClient esClient) throws ElasticsearchException, IOException {

		System.out.println("Reading main blockMark list..." );
		SearchResponse<BlockMark> response = esClient.search(s->s.index(BLOCK_MARK)
						.query(q->q
								.term(t->t
										.field("status")
										.value(MAIN)))
						.size(CHECK_POINTS_NUM)
						.sort(so->so.field(f->f
								.field("height")
								.order(SortOrder.Desc)))
				, BlockMark.class);

		List<Hit<BlockMark>> hitList = response.hits().hits();

		ArrayList<BlockMark> readList = new ArrayList<>();

		for(int i=hitList.size()-1; i>=0; i-- ) {
			readList.add(hitList.get(i).source());
		}

		return readList;
	}
}
