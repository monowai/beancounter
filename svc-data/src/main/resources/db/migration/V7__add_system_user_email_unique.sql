-- Add unique constraint on email for SystemUser
-- First, remove any duplicates keeping the oldest record
DELETE FROM system_user
WHERE id NOT IN (
    SELECT MIN(id)
    FROM system_user
    GROUP BY email
);

-- Add unique constraint
ALTER TABLE system_user
ADD CONSTRAINT uk_system_user_email UNIQUE (email);
