package dslabs.testsuites;

import org.junit.FixMethodOrder;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({Lab2Part1TestSuite.class, Lab2Part2TestSuite.class})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public interface Lab2TestSuite {
}
