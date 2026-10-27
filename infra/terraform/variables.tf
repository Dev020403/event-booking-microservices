variable "environment" {
  description = "Environment name, used in resource names and tags (e.g. local, dev)."
  type        = string
}

variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "aws_profile" {
  description = "Named AWS CLI profile holding the credentials to use."
  type        = string
}

variable "use_floci" {
  description = "When true, all AWS API calls go to the Floci emulator instead of real AWS."
  type        = bool
  default     = false
}

variable "floci_endpoint" {
  description = "Base URL of the Floci emulator. Only used when use_floci is true."
  type        = string
  default     = "http://localhost:4566"
}

variable "event_images_cors_allowed_origins" {
  description = "Browser origins allowed to upload and view event images via presigned URLs."
  type        = list(string)
  default     = []
}
