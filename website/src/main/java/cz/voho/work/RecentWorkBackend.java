package cz.voho.work;

import cz.voho.utility.ExecutorProvider;
import cz.voho.utility.LambdaClient;
import cz.voho.web.lambda.model.github.CommitMeta;
import cz.voho.web.lambda.model.github.LatestCommitsRequest;
import cz.voho.web.lambda.model.github.LatestCommitsResponse;
import cz.voho.web.lambda.model.photo.GetRecentPhotosRequest;
import cz.voho.web.lambda.model.photo.GetRecentPhotosResponse;
import cz.voho.web.lambda.model.photo.Image;
import cz.voho.web.lambda.model.sound.GetRecentSongsRequest;
import cz.voho.web.lambda.model.sound.GetRecentSongsResponse;
import cz.voho.web.lambda.model.sound.Song;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class RecentWorkBackend {
    public static final RecentWorkBackend SINGLETON = new RecentWorkBackend(new LambdaClient());

    private static final Logger LOG = LoggerFactory.getLogger(RecentWorkBackend.class);
    private static final int RECENT_PHOTO_COUNT = 6;
    private static final int RECENT_SONGS_COUNT = 4;
    private static final int UPDATE_INTERVAL_SECONDS = 300;
    private static final String WIKI_COMMIT_PATH = "website/src/main/resources/wiki/";
    private static final int RECENT_WIKI_UPDATES_COUNT = 10;

    private final ScheduledExecutorService scheduledExecutorService;
    private final LambdaClient lambdaClient;
    private final AtomicReference<GetRecentPhotosResponse> photosCache;
    private final AtomicReference<GetRecentSongsResponse> songsCache;
    private final AtomicReference<LatestCommitsResponse> recentWikiChangesCache;

    private RecentWorkBackend(final LambdaClient lambdaClient) {
        this.scheduledExecutorService = ExecutorProvider.INSTAGRAM_UPDATER_EXECUTOR;
        this.lambdaClient = lambdaClient;
        this.photosCache = new AtomicReference<>(null);
        this.songsCache = new AtomicReference<>(null);
        this.recentWikiChangesCache = new AtomicReference<>(null);

        this.scheduledExecutorService.scheduleWithFixedDelay(
                this::update,
                0,
                UPDATE_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    @Override
    protected void finalize() throws Throwable {
        scheduledExecutorService.shutdown();
    }

    private void update() {
        LOG.info("Updating...");
        updateRecentPhotos();
        updateRecentSongs();
        updateRecentWikiChanges();
        LOG.info("Updating complete.");
    }

    private void updateRecentWikiChanges() {
        final LatestCommitsRequest request = new LatestCommitsRequest();
        request.setPath(WIKI_COMMIT_PATH);

        final LatestCommitsResponse response = lambdaClient.callGitHubLambda(request);

        if (response != null && response.getCommits() != null && !response.getCommits().isEmpty()) {
            LOG.info("Updating recent wiki changes cache.");
            recentWikiChangesCache.set(response);
        } else {
            LOG.warn("Could not upgrade recent wiki changes cacheł.");
        }
    }

    private void updateRecentSongs() {
        final GetRecentSongsRequest request = new GetRecentSongsRequest();
        request.setCount(RECENT_SONGS_COUNT);

        final GetRecentSongsResponse response = lambdaClient.callSoundcloudLambda(request);

        if (response != null && response.getSongs() != null && response.getSongs().getSongs() != null) {
            LOG.info("Updating SoundCloud cache.");
            songsCache.set(response);
        } else {
            LOG.warn("Could not upgrade SoundCloud cache.");
        }
    }

    private void updateRecentPhotos() {
        final GetRecentPhotosRequest request = new GetRecentPhotosRequest();
        request.setCount(RECENT_PHOTO_COUNT);

        final GetRecentPhotosResponse response = lambdaClient.callInstagramLambda(request);

        if (response != null && response.getRecentItems() != null && response.getRecentItems().getData() != null) {
            LOG.info("Updating Instagram cache.");
            photosCache.set(response);
        } else {
            LOG.warn("Could not upgrade Instagram cache.");
        }
    }

    public List<Song> getRecentTracks() {
        final GetRecentSongsResponse latestValue = songsCache.get();

        if (latestValue == null) {
            return Collections.emptyList();
        }

        return latestValue.getSongs().getSongs();
    }

    public List<Image> getRecentPhotos() {
        final GetRecentPhotosResponse latestValue = photosCache.get();

        if (latestValue == null) {
            return Collections.emptyList();
        }

        return latestValue.getRecentItems().getData();
    }

    public List<CommitMeta> getRecentWikiChanges() {
        final LatestCommitsResponse latestValue = recentWikiChangesCache.get();

        if (latestValue == null) {
            return Collections.emptyList();
        }

        return latestValue.getCommits().subList(0, RECENT_WIKI_UPDATES_COUNT);
    }
}
