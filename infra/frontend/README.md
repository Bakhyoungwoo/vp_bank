# Frontend deployment

The frontend is a static HTML/CSS/JS site in `C:\Users\5131\Desktop\vp\vp_front`.
Terraform creates a private S3 bucket and CloudFront distribution. CloudFront serves
the frontend over HTTPS and forwards `/api/*` to the EC2 k3s ingress.

After Terraform has created the bucket and distribution:

```powershell
.\deploy.ps1
```

Or specify another local frontend directory:

```powershell
.\deploy.ps1 -FrontendPath "D:\path\to\vp_front"
```

The CloudFront default domain works without a custom domain or ACM certificate.
