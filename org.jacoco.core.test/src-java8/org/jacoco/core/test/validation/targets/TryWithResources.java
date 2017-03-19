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
package org.jacoco.core.test.validation.targets;

import static org.jacoco.core.test.validation.targets.Stubs.nop;

import java.io.Closeable;
import java.io.IOException;

/**
 * This test target is a try-with-resources statement (Java 7).
 */
public class TryWithResources {

	private static class Resource implements Closeable {
		@Override
		public void close() {
		}
	}

	private static void one() throws Exception {
		nop(); // $line-before$
		try ( // $line-try$
				Resource r = new Resource() // $line-open$
		) {
			nop(r); // $line-body$
		} // $line-close$
		nop(); // $line-after$
	}

	private static void two() throws Exception {
		nop(); // $line-two.before$
		try ( // $line-two.try$
				Resource r1 = new Resource(); // $line-two.open1$
				Closeable r2 = new Resource() // $line-two.open2$
		) {
			nop(r1.toString() + r2.toString()); // $line-two.body$
		} // $line-two.close$
		nop(); // $line-two.after$
	}

	/**
	 * Closing performed using {@link org.objectweb.asm.Opcodes#INVOKEVIRTUAL}
	 * or {@link org.objectweb.asm.Opcodes#INVOKEINTERFACE} depending on a class
	 * of resource.
	 */
	private static void three() throws Exception {
		nop(); // $line-three.before$
		try ( // $line-three.try$
				Resource r1 = new Resource(); // $line-three.open1$
				Closeable r2 = new Resource(); // $line-three.open2$
				AutoCloseable r3 = new Resource() // $line-three.open3$
		) {
			nop(r1.toString() + r2.toString() + r3.toString()); // $line-three.body$
		} // $line-three.close$
		nop(); // $line-three.after$
	}

	/**
	 * Both javac and ECJ place additional handlers after code that closes
	 * resources. In other words
	 * 
	 * <pre>
	 *     try ResourceSpecification
	 *         Block
	 *     [Catches]
	 *     [Finally]
	 * </pre>
	 * 
	 * is equivalent to
	 * 
	 * <pre>
	 *     try {
	 *         try ResourceSpecification
	 *             Block
	 *     }
	 *     [Catches]
	 *     [Finally]
	 * </pre>
	 */
	private static void extended() {
		nop(); // $line-extended.before$
		try ( // $line-extended.try$
				Closeable r = new Resource() // $line-extended.open$
		) {
			nop(r); // $line-extended.body$
		} // $line-extended.close$
		catch (IOException e) { // $line-extended.catch$
			nop(); // $line-extended.catchBlock$
		} // $line-extended.catchBlockEnd$
		nop(); // $line-extended.after$
	}

	private static Object returnInBody() throws IOException {
		try ( // $line-returnInBody.try$
				Closeable r = new Resource() // $line-returnInBody.open$
		) {
			return read(r); // $line-returnInBody.return$
		} // $line-returnInBody.close$
	}

	private static Object read(Object r) {
		return r;
	}

	public static void main(String[] args) throws Exception {
		one();
		two();
		three();

		extended();

		returnInBody();

		empty();
		handwritten();
	}

	// Corner cases

	private static void empty() throws Exception {
		try ( // $line-empty.try$
				@SuppressWarnings("unused")
				Closeable r = new Resource() // $line-empty.open$
		) {
		} // $line-empty.close$
	}

	private static void handwritten() throws IOException {
		Closeable r = new Resource();
		Throwable primaryExc = null;
		try {
			nop(r);
		} catch (Throwable t) {
			primaryExc = t;
			throw t;
		} finally {
			if (r != null) { // $line-handwritten$
				if (primaryExc != null) {
					try {
						r.close();
					} catch (Throwable suppressedExc) {
						primaryExc.addSuppressed(suppressedExc);
					}
				} else {
					r.close();
				}
			}
		}
	}

	private static void throwInBody() throws IOException {
		try (Closeable r = new Resource()) {
			nop(r);
			throw new RuntimeException(); // $line-throwInBody.throw$
		} // $line-throwInBody$
	}

}
