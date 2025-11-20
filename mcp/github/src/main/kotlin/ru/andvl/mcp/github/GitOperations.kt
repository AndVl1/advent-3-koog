package ru.andvl.mcp.github

import org.slf4j.LoggerFactory
import java.io.File

/**
 * Helper class for Git operations on local repositories
 */
class GitOperations {
    private val logger = LoggerFactory.getLogger(GitOperations::class.java)

    /**
     * Execute a git command in a repository directory
     */
    private fun executeGitCommand(
        repositoryPath: String,
        vararg command: String
    ): Result<String> {
        return try {
            val dir = File(repositoryPath)
            if (!dir.exists() || !dir.isDirectory) {
                return Result.failure(IllegalArgumentException("Repository path does not exist or is not a directory"))
            }

            logger.info("Executing git command in $repositoryPath: ${command.joinToString(" ")}")

            val process = ProcessBuilder(*command)
                .directory(dir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Result.success(output)
            } else {
                Result.failure(RuntimeException("Git command failed with exit code $exitCode: $output"))
            }
        } catch (e: Exception) {
            logger.error("Error executing git command", e)
            Result.failure(e)
        }
    }

    /**
     * Create a new branch in a local repository
     */
    fun createBranch(
        repositoryPath: String,
        branchName: String,
        baseBranch: String? = null
    ): Result<String> {
        return try {
            // Checkout to base branch if specified
            if (baseBranch != null) {
                executeGitCommand(repositoryPath, "git", "checkout", baseBranch).getOrElse {
                    return Result.failure(it)
                }
            }

            // Create and checkout new branch
            executeGitCommand(repositoryPath, "git", "checkout", "-b", branchName).getOrElse {
                return Result.failure(it)
            }

            Result.success("Branch '$branchName' created successfully")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a commit with changes
     */
    fun commit(
        repositoryPath: String,
        message: String,
        files: List<String>? = null
    ): Result<String> {
        return try {
            // Add files
            if (files != null && files.isNotEmpty()) {
                for (file in files) {
                    executeGitCommand(repositoryPath, "git", "add", file).getOrElse {
                        return Result.failure(it)
                    }
                }
            } else {
                // Add all changes
                executeGitCommand(repositoryPath, "git", "add", "-A").getOrElse {
                    return Result.failure(it)
                }
            }

            // Commit
            executeGitCommand(repositoryPath, "git", "commit", "-m", message).getOrElse {
                return Result.failure(it)
            }

            // Get commit SHA
            val sha = executeGitCommand(repositoryPath, "git", "rev-parse", "HEAD").getOrElse {
                return Result.failure(it)
            }.trim()

            Result.success(sha)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Push changes to remote repository
     */
    fun push(
        repositoryPath: String,
        branchName: String,
        force: Boolean = false
    ): Result<String> {
        return try {
            val command = if (force) {
                arrayOf("git", "push", "--force", "origin", branchName)
            } else {
                arrayOf("git", "push", "-u", "origin", branchName)
            }

            executeGitCommand(repositoryPath, *command).getOrElse {
                // Check if rejected
                if (it.message?.contains("rejected") == true) {
                    return Result.failure(RuntimeException("Push rejected: ${it.message}"))
                }
                return Result.failure(it)
            }

            Result.success("Pushed branch '$branchName' successfully")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get diff between two branches/commits
     */
    fun getDiff(
        repositoryPath: String,
        base: String,
        head: String
    ): Result<Map<String, Any>> {
        return try {
            // Get diff
            val diff = executeGitCommand(repositoryPath, "git", "diff", "$base...$head").getOrElse {
                return Result.failure(it)
            }

            // Get files changed
            val filesChangedOutput = executeGitCommand(
                repositoryPath,
                "git", "diff", "--name-status", "$base...$head"
            ).getOrElse {
                return Result.failure(it)
            }

            val filesChanged = filesChangedOutput.trim().split("\n").filter { it.isNotBlank() }

            // Get stats
            val statsOutput = executeGitCommand(
                repositoryPath,
                "git", "diff", "--shortstat", "$base...$head"
            ).getOrElse {
                return Result.failure(it)
            }

            // Parse stats (e.g., "3 files changed, 15 insertions(+), 5 deletions(-)")
            val stats = statsOutput.trim()
            val insertions = stats.substringAfter("insertions(+)", "0").substringBefore(",").trim().toIntOrNull() ?: 0
            val deletions = stats.substringAfter("deletions(-)", "0").trim().toIntOrNull() ?: 0

            Result.success(
                mapOf(
                    "diff" to diff,
                    "files_changed" to filesChanged.size,
                    "insertions" to insertions,
                    "deletions" to deletions
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get current branch name
     */
    fun getCurrentBranch(repositoryPath: String): Result<String> {
        return try {
            val branch = executeGitCommand(repositoryPath, "git", "rev-parse", "--abbrev-ref", "HEAD")
                .getOrElse { return Result.failure(it) }
                .trim()

            Result.success(branch)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checkout (switch to) an existing branch
     */
    fun checkoutBranch(
        repositoryPath: String,
        branchName: String
    ): Result<String> {
        return try {
            logger.info("Checking out branch: $branchName")

            executeGitCommand(repositoryPath, "git", "checkout", branchName).getOrElse {
                return Result.failure(it)
            }

            Result.success("Switched to branch '$branchName' successfully")
        } catch (e: Exception) {
            logger.error("Failed to checkout branch: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Clone a repository from URL
     */
    fun cloneRepository(
        repositoryUrl: String,
        targetDirectory: String
    ): Result<String> {
        return try {
            val targetDir = File(targetDirectory)

            // Check if directory already exists
            if (targetDir.exists()) {
                logger.warn("Target directory already exists: $targetDirectory")
                return Result.success(targetDirectory)
            }

            // Create parent directory if it doesn't exist
            targetDir.parentFile?.mkdirs()

            logger.info("Cloning repository $repositoryUrl to $targetDirectory")

            val process = ProcessBuilder("git", "clone", repositoryUrl, targetDirectory)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                logger.info("Repository cloned successfully")
                Result.success(targetDirectory)
            } else {
                logger.error("Failed to clone repository: $output")
                Result.failure(RuntimeException("Git clone failed with exit code $exitCode: $output"))
            }
        } catch (e: Exception) {
            logger.error("Error cloning repository", e)
            Result.failure(e)
        }
    }
}
