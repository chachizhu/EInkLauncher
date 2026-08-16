package com.sousoulab.einklauncher

import androidx.core.content.FileProvider

/** Exposes only verified APKs from cache/updates to the system package installer. */
class UpdateFileProvider : FileProvider()
