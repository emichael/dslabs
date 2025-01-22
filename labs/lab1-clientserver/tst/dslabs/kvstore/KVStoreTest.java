package dslabs.kvstore;

import static dslabs.kvstore.KVStoreWorkload.append;
import static dslabs.kvstore.KVStoreWorkload.appendResult;
import static dslabs.kvstore.KVStoreWorkload.get;
import static dslabs.kvstore.KVStoreWorkload.getResult;
import static dslabs.kvstore.KVStoreWorkload.keyNotFound;
import static dslabs.kvstore.KVStoreWorkload.put;
import static dslabs.kvstore.KVStoreWorkload.putOk;
import static org.junit.Assert.assertEquals;

import dslabs.framework.testing.junit.DSLabsJUnitTest;
import dslabs.framework.testing.junit.DSLabsTestRunner;
import dslabs.framework.testing.junit.Lab;
import dslabs.framework.testing.junit.Part;
import dslabs.framework.testing.junit.TestDescription;
import dslabs.framework.testing.junit.TestPointValue;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@Lab("1")
@Part(1)
@RunWith(DSLabsTestRunner.class)
public class KVStoreTest extends DSLabsJUnitTest {
  private KVStore kvStore;

  @Before
  public void setup() {
    kvStore = new KVStore();
  }

  @Test(timeout = 5 * 1000)
  @TestPointValue(5)
  @TestDescription("Basic key-value operations")
  public void test01BasicKVTests() {
    assertEquals(keyNotFound(), kvStore.execute(get("FOO")));
    assertEquals(putOk(), kvStore.execute(put("FOO", "BAR")));
    assertEquals(appendResult("BARBAZ"), kvStore.execute(append("FOO", "BAZ")));
    assertEquals(appendResult("BARBAZBAZ"), kvStore.execute(append("FOO", "BAZ")));
    assertEquals(appendResult("BAR2"), kvStore.execute(append("FOO2", "BAR2")));
    assertEquals(putOk(), kvStore.execute(put("FOO2", "BAZ2")));
    assertEquals(getResult("BAZ2"), kvStore.execute(get("FOO2")));
    assertEquals(putOk(), kvStore.execute(put("fizz", "buzz")));
    assertEquals(getResult("buzz"), kvStore.execute(get("fizz")));
    assertEquals(getResult("BARBAZBAZ"), kvStore.execute(get("FOO")));
    assertEquals(appendResult("BARBAZBAZ[c:1, v:2]"), kvStore.execute(append("FOO", "[c:1, v:2]")));
    assertEquals(getResult("BARBAZBAZ[c:1, v:2]"), kvStore.execute(get("FOO")));

    String value = RandomStringUtils.randomAscii(1000);
    assertEquals(putOk(), kvStore.execute(put("key", value)));
    assertEquals(getResult(value), kvStore.execute(get("key")));
  }
}
