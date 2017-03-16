/*******************************************************************************
 * Copyright (c) 2009, 2017 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.test.validation;

import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.internal.Java9Support;
import org.jacoco.core.test.TargetLoader;
import org.jacoco.core.test.validation.targets.TryWithResources;
import org.junit.Test;

import java.io.IOException;

/**
 * Test of filtering of a bytecode that is generated for a try-with-resources
 * statement (Java 7).
 */
public class TryWithResourcesTest extends ValidationTestBase {

	public TryWithResourcesTest() {
		super("src-java8", TryWithResources.class);
	}

	/**
	 * {@link TryWithResources#one()}
	 */
	@Test
	public void one() {
		assertLine("before", ICounter.FULLY_COVERED);
		// without filter next line covered partly:
		assertLine("try", ICounter.FULLY_COVERED);
		assertLine("open", ICounter.FULLY_COVERED);
		assertLine("body", ICounter.FULLY_COVERED);
		// without filter next line has branches with javac < 9,
		// and only instructions with javac 9
		// (see https://bugs.openjdk.java.net/browse/JDK-7020499):
		assertLine("close", ICounter.EMPTY);
		assertLine("after", ICounter.FULLY_COVERED);
	}

	/**
	 * {@link TryWithResources#two()}
	 */
	@Test
	public void two() {
		assertLine("two.before", ICounter.FULLY_COVERED);
		// without filter next line covered partly:
		assertLine("two.try", ICounter.FULLY_COVERED);
		assertLine("two.open1", ICounter.FULLY_COVERED);
		assertLine("two.open2", ICounter.FULLY_COVERED);
		assertLine("two.body", ICounter.FULLY_COVERED);
		// without filter next line has branches:
		assertLine("two.close", ICounter.EMPTY);
		assertLine("three.after", ICounter.FULLY_COVERED);
	}

	/**
	 * {@link TryWithResources#three()}
	 */
	@Test
	public void three() {
		assertLine("three.before", ICounter.FULLY_COVERED);
		// without filter next line covered partly:
		assertLine("three.try", ICounter.FULLY_COVERED);
		assertLine("three.open1", ICounter.FULLY_COVERED);
		assertLine("three.open2", ICounter.FULLY_COVERED);
		assertLine("three.open3", ICounter.FULLY_COVERED);
		assertLine("three.body", ICounter.FULLY_COVERED);
		// without filter next line has branches:
		assertLine("three.close", ICounter.EMPTY);
		assertLine("three.after", ICounter.FULLY_COVERED);
	}

	/**
	 * {@link TryWithResources#extended()}
	 */
	@Test
	public void extended() {
		assertLine("extended.before", ICounter.FULLY_COVERED);
		// without filter next line covered partly:
		assertLine("extended.try", ICounter.FULLY_COVERED, 0, 0);
		assertLine("extended.open", ICounter.FULLY_COVERED);
		assertLine("extended.body", ICounter.FULLY_COVERED);
		// without filter next line has branches:
		assertLine("extended.close", ICounter.EMPTY);
		assertLine("extended.catch", ICounter.NOT_COVERED);
		assertLine("extended.catchBlock", ICounter.NOT_COVERED);
		assertLine("extended.catchBlockEnd", ICounter.FULLY_COVERED);
		assertLine("extended.after", ICounter.FULLY_COVERED);
	}

	/**
	 * {@link TryWithResources#returnInBody()}
	 */
	@Test
	public void returnInBody() {
		assertLine("returnInBody.try", ICounter.FULLY_COVERED);
		assertLine("returnInBody.open", ICounter.FULLY_COVERED);
		assertLine("returnInBody.close", ICounter.EMPTY);
		assertLine("returnInBody.return", ICounter.FULLY_COVERED);
	}

	/**
	 * {@link TryWithResources#handwritten()}
	 */
	@Test
	public void handwritten() {
		assertLine("handwritten", ICounter.EMPTY);
	}

	/**
	 * {@link TryWithResources#empty()}
	 */
	@Test
	public void empty() throws IOException {
		final boolean java9 = Java9Support.isPatchRequired(
				TargetLoader.getClassDataAsBytes(TryWithResources.class));

		assertLine("empty.try", ICounter.FULLY_COVERED, 0, 0);
		assertLine("empty.open", ICounter.FULLY_COVERED);
		if (java9) {
			assertLine("empty.close", ICounter.FULLY_COVERED, 0, 0);
		} else {
			assertLine("empty.close", ICounter.PARTLY_COVERED, 2, 2);
		}
	}

	/**
	 * {@link TryWithResources#throwInBody()}
	 */
	@Test
	public void throwInBody() throws Exception {
		final boolean java9 = Java9Support.isPatchRequired(
				TargetLoader.getClassDataAsBytes(TryWithResources.class));

		assertLine("throwInBody", ICounter.NOT_COVERED, java9 ? 0 : 4, 0);
		assertLine("throwInBody.throw", ICounter.NOT_COVERED);
	}

}
