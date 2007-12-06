package com.darwinsys.security;

import com.darwinsys.security.DESCrypter;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DESUtilsTest {

	DESCrypter target;
	
	@Before
	public void setUp() throws Exception {
		final String myPass = "Ain't no binary blobs here!";
		target = new DESCrypter(myPass);
	}

	@Test
	public void testDES() {

		final String clear = "Once, upon a midnight dreary...";
		
		System.out.println("Original is " + clear);

		String encrypted = target.encrypt(clear);
		System.out.println("Encrypts as " + encrypted);

		String decrypted = target.decrypt(encrypted);

		assertEquals("Decrypted OK!", clear, decrypted);
	}
}
