package org.testcontainers.containers;

import org.apache.commons.lang.RandomStringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

/**
 * Tests if the password passed to the container satisfied the password policy described at
 * https://docs.microsoft.com/en-us/sql/relational-databases/security/password-policy?view=sql-server-2017
 *
 * @author Enrico Costanzi
 */
@RunWith(Parameterized.class)
public class MSSQLServerContainerPasswordTest {

    private static String UPPER_CASE_LETTERS = "ABCDE";
    private static String LOWER_CASE_LETTERS = "abcde";
    private static String NUMBERS = "12345";
    private static String SPECIAL_CHARS = "_(!)_";

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { null, false },
            //to short
            { "abc123", false },

            //too long
            { RandomStringUtils.randomAlphabetic(129), false},

            //only 2 categories
            { UPPER_CASE_LETTERS + NUMBERS, false },
            { UPPER_CASE_LETTERS + SPECIAL_CHARS, false },
            { LOWER_CASE_LETTERS + NUMBERS, false },
            { LOWER_CASE_LETTERS + SPECIAL_CHARS, false },
            { NUMBERS + SPECIAL_CHARS, false },

            //3 categories
            { UPPER_CASE_LETTERS + LOWER_CASE_LETTERS + NUMBERS, true},
            { UPPER_CASE_LETTERS + LOWER_CASE_LETTERS + SPECIAL_CHARS, true},
            { UPPER_CASE_LETTERS + NUMBERS + SPECIAL_CHARS, true},
            { LOWER_CASE_LETTERS + NUMBERS + SPECIAL_CHARS, true},

            //4 categories
            { UPPER_CASE_LETTERS + LOWER_CASE_LETTERS + NUMBERS + SPECIAL_CHARS, true},


        });
    }

    private String password;
    private Boolean valid;

    public MSSQLServerContainerPasswordTest(String password, Boolean valid){
        this.password = password;
        this.valid = valid;
    }

    @Test
    public void runPasswordTests() {
        try {
            new MSSQLServerContainer().withPassword(this.password);
            if(!valid)  Assert.fail("Password " + this.password + " is not valid. Expected exception");
        } catch (IllegalArgumentException e){
            if(valid) Assert.fail("Password " + this.password + " should have been validated");
        }
    }


}
