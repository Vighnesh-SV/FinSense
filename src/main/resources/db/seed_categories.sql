-- Built-in categories (user_id = NULL). Run only when the table is empty.
-- nature: ESSENTIAL = needed to live/work, DISCRETIONARY = optional "wants".
-- is_commitment = 1 marks EMI-style payments that are already tracked in the
-- commitments table, so they are not counted twice as ordinary spending.

-- Income
INSERT INTO categories (name, type, nature) VALUES ('Salary', 'INCOME', 'ESSENTIAL');
INSERT INTO categories (name, type, nature) VALUES ('Freelance', 'INCOME', 'ESSENTIAL');
INSERT INTO categories (name, type, nature) VALUES ('Business', 'INCOME', 'ESSENTIAL');
INSERT INTO categories (name, type, nature) VALUES ('Investments', 'INCOME', 'ESSENTIAL');
INSERT INTO categories (name, type, nature) VALUES ('Gifts Received', 'INCOME', 'ESSENTIAL');
INSERT INTO categories (name, type, nature) VALUES ('Other Income', 'INCOME', 'ESSENTIAL');

-- Essential expenses
INSERT INTO categories (name, type, nature) VALUES ('Rent', 'EXPENSE', 'ESSENTIAL');
INSERT INTO categories (name, type, nature) VALUES ('Groceries', 'EXPENSE', 'ESSENTIAL');
INSERT INTO categories (name, type, nature) VALUES ('Utilities', 'EXPENSE', 'ESSENTIAL');
INSERT INTO categories (name, type, nature) VALUES ('Transport', 'EXPENSE', 'ESSENTIAL');
INSERT INTO categories (name, type, nature) VALUES ('Healthcare', 'EXPENSE', 'ESSENTIAL');
INSERT INTO categories (name, type, nature) VALUES ('Education', 'EXPENSE', 'ESSENTIAL');
INSERT INTO categories (name, type, nature) VALUES ('Insurance', 'EXPENSE', 'ESSENTIAL');
INSERT INTO categories (name, type, nature, is_commitment) VALUES ('EMI / Loan Repayment', 'EXPENSE', 'ESSENTIAL', TRUE);

-- Discretionary expenses
INSERT INTO categories (name, type, nature) VALUES ('Dining Out', 'EXPENSE', 'DISCRETIONARY');
INSERT INTO categories (name, type, nature) VALUES ('Shopping', 'EXPENSE', 'DISCRETIONARY');
INSERT INTO categories (name, type, nature) VALUES ('Electronics', 'EXPENSE', 'DISCRETIONARY');
INSERT INTO categories (name, type, nature) VALUES ('Entertainment', 'EXPENSE', 'DISCRETIONARY');
INSERT INTO categories (name, type, nature) VALUES ('Travel', 'EXPENSE', 'DISCRETIONARY');
INSERT INTO categories (name, type, nature) VALUES ('Subscriptions', 'EXPENSE', 'DISCRETIONARY');
INSERT INTO categories (name, type, nature) VALUES ('Personal Care', 'EXPENSE', 'DISCRETIONARY');
INSERT INTO categories (name, type, nature) VALUES ('Gifts & Donations', 'EXPENSE', 'DISCRETIONARY');
INSERT INTO categories (name, type, nature) VALUES ('Other Expense', 'EXPENSE', 'DISCRETIONARY');
