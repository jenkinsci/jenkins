package hudson.tasks.junit;

import hudson.tasks.test.TestResult;
import junit.framework.TestCase;

public class ClassResultTest extends TestCase {
	
	public void testFindCorrespondingResult() {
		ClassResult classResult = new ClassResult(null, "com.example.ExampleTest");
	
		CaseResult caseResult = new CaseResult(null, "testCase", null);
	
		classResult.add(caseResult);
	
		TestResult result = classResult.findCorrespondingResult("extraprefix.com.example.ExampleTest.testCase");
		assertEquals(caseResult, result);
	}

	public void testFindCorrespondingResultWhereClassResultNameIsNotSubstring() {
		ClassResult classResult = new ClassResult(null, "aaaa");
	
		CaseResult caseResult = new CaseResult(null, "tc_bbbb", null);
	
		classResult.add(caseResult);
	
		TestResult result = classResult.findCorrespondingResult("tc_bbbb");
		assertEquals(caseResult, result);
	}

	public void testFindCorrespondingResultWhereClassResultNameIsLastInCaseResultName() {
		ClassResult classResult = new ClassResult(null, "aaaa");
	
		CaseResult caseResult = new CaseResult(null, "tc_aaaa", null);
	
		classResult.add(caseResult);
	
		TestResult result = classResult.findCorrespondingResult("tc_aaaa");
		assertEquals(caseResult, result);
	}

}
