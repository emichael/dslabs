package dslabs.testsuites;


import org.junit.FixMethodOrder;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({Lab1Part1TestSuite.class, Lab1Part2TestSuite.class,
                      Lab1Part3TestSuite.class})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public interface Lab1TestSuite {
}
