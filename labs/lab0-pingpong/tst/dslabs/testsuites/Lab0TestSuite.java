package dslabs.testsuites;


import dslabs.pingpong.PingTest;
import org.junit.FixMethodOrder;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses(PingTest.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public interface Lab0TestSuite {
}
