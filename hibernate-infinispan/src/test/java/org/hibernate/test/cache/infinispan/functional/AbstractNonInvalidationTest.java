package org.hibernate.test.cache.infinispan.functional;

import org.hibernate.PessimisticLockException;
import org.hibernate.StaleStateException;
import org.hibernate.cache.infinispan.InfinispanRegionFactory;
import org.hibernate.cache.infinispan.entity.EntityRegionImpl;
import org.hibernate.cache.infinispan.util.InfinispanMessageLogger;
import org.hibernate.cache.spi.Region;
import org.hibernate.test.cache.infinispan.functional.entities.Item;
import org.hibernate.test.cache.infinispan.util.TestInfinispanRegionFactory;
import org.hibernate.test.cache.infinispan.util.TestTimeService;
import org.hibernate.testing.AfterClassOnce;
import org.hibernate.testing.BeforeClassOnce;
import org.infinispan.AdvancedCache;
import org.junit.After;
import org.junit.Before;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Common base for TombstoneTest and VersionedTest
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class AbstractNonInvalidationTest extends SingleNodeTest {
   protected static final int WAIT_TIMEOUT = 2000;
   protected static final TestTimeService TIME_SERVICE = new TestTimeService();

   protected long TIMEOUT;
   protected ExecutorService executor;
   protected InfinispanMessageLogger log = InfinispanMessageLogger.Provider.getLog(getClass());
   protected AdvancedCache entityCache;
   protected long itemId;
   protected Region region;
   protected long timeout;

   @BeforeClassOnce
   public void setup() {
      executor = Executors.newCachedThreadPool(new ThreadFactory() {
         AtomicInteger counter = new AtomicInteger();

         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "Executor-" +  counter.incrementAndGet());
         }
      });
   }

   @AfterClassOnce
   public void shutdown() {
      executor.shutdown();
   }

   @Override
   protected void startUp() {
      super.startUp();
      InfinispanRegionFactory regionFactory = (InfinispanRegionFactory) sessionFactory().getSettings().getRegionFactory();
      TIMEOUT = regionFactory.getPendingPutsCacheConfiguration().expiration().maxIdle();
      region = sessionFactory().getSecondLevelCacheRegion(Item.class.getName());
      entityCache = ((EntityRegionImpl) region).getCache();
   }

   @Before
   public void insertAndClearCache() throws Exception {
      region = sessionFactory().getSecondLevelCacheRegion(Item.class.getName());
      entityCache = ((EntityRegionImpl) region).getCache();
      timeout = ((EntityRegionImpl) region).getRegionFactory().getPendingPutsCacheConfiguration().expiration().maxIdle();
      Item item = new Item("my item", "Original item");
      withTxSession(s -> s.persist(item));
      entityCache.clear();
      assertEquals("Cache is not empty", Collections.EMPTY_SET, entityCache.keySet());
      itemId = item.getId();
      log.info("Insert and clear finished");
   }

   @After
   public void cleanup() throws Exception {
      withTxSession(s -> {
         s.createQuery("delete from Item").executeUpdate();
      });
   }

   protected Future<Boolean> removeFlushWait(long id, CyclicBarrier loadBarrier, CountDownLatch preFlushLatch, CountDownLatch flushLatch, CountDownLatch commitLatch) throws Exception {
      return executor.submit(() -> withTxSessionApply(s -> {
         try {
            Item item = s.load(Item.class, id);
            item.getName(); // force load & putFromLoad before the barrier
            loadBarrier.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
            s.delete(item);
            if (preFlushLatch != null) {
               awaitOrThrow(preFlushLatch);
            }
            s.flush();
         } catch (StaleStateException e) {
            log.info("Exception thrown: ", e);
            markRollbackOnly(s);
            return false;
         } catch (PessimisticLockException e) {
            log.info("Exception thrown: ", e);
            markRollbackOnly(s);
            return false;
         } finally {
            if (flushLatch != null) {
               flushLatch.countDown();
            }
         }
         awaitOrThrow(commitLatch);
         return true;
      }));
   }

   protected Future<Boolean> updateFlushWait(long id, CyclicBarrier loadBarrier, CountDownLatch preFlushLatch, CountDownLatch flushLatch, CountDownLatch commitLatch) throws Exception {
      return executor.submit(() -> withTxSessionApply(s -> {
         try {
            Item item = s.load(Item.class, id);
            item.getName(); // force load & putFromLoad before the barrier
            loadBarrier.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
            item.setDescription("Updated item");
            s.update(item);
            if (preFlushLatch != null) {
               awaitOrThrow(preFlushLatch);
            }
            s.flush();
         } catch (StaleStateException e) {
            log.info("Exception thrown: ", e);
            markRollbackOnly(s);
            return false;
         } catch (PessimisticLockException e) {
            log.info("Exception thrown: ", e);
            markRollbackOnly(s);
            return false;
         } finally {
            if (flushLatch != null) {
               flushLatch.countDown();
            }
         }
         awaitOrThrow(commitLatch);
         return true;
      }));
   }

   protected Future<Boolean> evictWait(long id, CyclicBarrier loadBarrier, CountDownLatch preEvictLatch, CountDownLatch postEvictLatch) throws Exception {
      return executor.submit(() -> {
         try {
            loadBarrier.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
            if (preEvictLatch != null) {
               awaitOrThrow(preEvictLatch);
            }
            sessionFactory().getCache().evictEntity(Item.class, id);
         } finally {
            if (postEvictLatch != null) {
               postEvictLatch.countDown();
            }
         }
         return true;
      });
   }

   protected void awaitOrThrow(CountDownLatch latch) throws InterruptedException, TimeoutException {
      if (!latch.await(WAIT_TIMEOUT, TimeUnit.SECONDS)) {
         throw new TimeoutException();
      }
   }

   @Override
   protected void addSettings(Map settings) {
      super.addSettings(settings);
      settings.put(TestInfinispanRegionFactory.TIME_SERVICE, TIME_SERVICE);
   }
}
